import AVFoundation

// MARK: - Delegate Protocol

protocol AudioEngineDelegate: AnyObject {
    func onPitchDetected(detectedHz: Double, targetHz: Double, deviationCents: Double)
    func onSilence()
    func onError(_ message: String)
    func onRecordingSaved(url: URL, durationMs: Int64)
}

// MARK: - AudioEngine

final class AudioEngine {

    static let shared = AudioEngine()

    weak var delegate: AudioEngineDelegate?

    var activeMaqam: Maqam?
    var rootHz: Double = 293.66
    var bypassMode = false
    var sensitivity = 1.0
    var correctionSpeed = 0.30

    private let avEngine = AVAudioEngine()
    private var sourceNode: AVAudioSourceNode?
    private let sampleRate = 44100.0
    private let detectBufSize = 4096
    private let processBufSize = 1024

    private var detector: PitchDetector
    private var shifter: PitchShifter
    private var writer = RecordingWriter()

    // Detection ring buffer
    private var detectRing: [Float]
    private var detectPos = 0

    // Output ring buffer (16384 samples)
    private var outBuf: [Float]
    private var outWrPos = 0
    private var outRdPos = 0
    private var outFill = 0

    private var currentRatio = 1.0
    private var voiceWasActive = false
    private var uiCounter = 0
    private var recStartMs: Int64 = 0

    // Serial queue for audio processing to protect shared state
    private let audioQueue = DispatchQueue(label: "com.autotune.audio", qos: .userInteractive)

    private init() {
        detector = PitchDetector(sampleRate: 44100, bufferSize: 4096)
        shifter = PitchShifter(sampleRate: 44100.0)
        detectRing = [Float](repeating: 0, count: 4096)
        outBuf = [Float](repeating: 0, count: 16384)
    }

    var isRunning: Bool { avEngine.isRunning }
    var isRecording: Bool { writer.isActive }

    // MARK: - Start / Stop

    func start() throws {
        guard !avEngine.isRunning else { return }

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord,
                                mode: .measurement,
                                options: [.defaultToSpeaker, .mixWithOthers, .allowBluetooth])
        try session.setPreferredSampleRate(sampleRate)
        try session.setPreferredIOBufferDuration(Double(processBufSize) / sampleRate)
        try session.setActive(true)

        let fmt = AVAudioFormat(commonFormat: .pcmFormatFloat32,
                                sampleRate: sampleRate,
                                channels: 1,
                                interleaved: true)!

        // Output source node — reads from outBuf ring
        let src = AVAudioSourceNode(format: fmt) { [weak self] _, _, frameCount, ablPtr -> OSStatus in
            guard let s = self else { return noErr }
            let buf = UnsafeMutableAudioBufferListPointer(ablPtr)[0]
            let out = buf.mData!.assumingMemoryBound(to: Float.self)
            let n = Int(frameCount)

            s.audioQueue.sync {
                let avail = s.outFill
                let toRead = min(n, avail)
                for i in 0..<toRead {
                    out[i] = s.outBuf[(s.outRdPos + i) % s.outBuf.count]
                }
                for i in toRead..<n { out[i] = 0 }
                s.outRdPos = (s.outRdPos + toRead) % s.outBuf.count
                s.outFill = max(0, s.outFill - n)
            }
            return noErr
        }
        sourceNode = src
        avEngine.attach(src)
        avEngine.connect(src, to: avEngine.mainMixerNode, format: fmt)

        // Input tap
        avEngine.inputNode.installTap(onBus: 0,
                                      bufferSize: AVAudioFrameCount(processBufSize),
                                      format: nil) { [weak self] buf, _ in
            self?.processInput(buf)
        }

        // Reset state
        audioQueue.sync {
            self.detectPos = 0
            self.detectRing = [Float](repeating: 0, count: self.detectBufSize)
            self.outBuf = [Float](repeating: 0, count: self.outBuf.count)
            self.outWrPos = 0
            self.outRdPos = 0
            self.outFill = 0
            self.currentRatio = 1.0
            self.voiceWasActive = false
            self.shifter.reset()
        }

        try avEngine.start()
    }

    func stop() {
        if writer.isActive { _ = stopRecording() }
        avEngine.inputNode.removeTap(onBus: 0)
        avEngine.stop()
        if let src = sourceNode {
            avEngine.detach(src)
            sourceNode = nil
        }
        try? AVAudioSession.sharedInstance().setActive(false)
    }

    // MARK: - Recording

    func startRecording(saveDir: URL) -> Bool {
        guard isRunning else { return false }
        do {
            _ = try writer.start(saveDir: saveDir)
            recStartMs = Int64(Date().timeIntervalSince1970 * 1000)
            return true
        } catch {
            delegate?.onError("فشل بدء التسجيل: \(error.localizedDescription)")
            return false
        }
    }

    func stopRecording() -> URL? {
        let url = writer.stop()
        if let u = url {
            let dur = Int64(Date().timeIntervalSince1970 * 1000) - recStartMs
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.onRecordingSaved(url: u, durationMs: dur)
            }
        }
        return url
    }

    // MARK: - Input Processing

    private func processInput(_ buffer: AVAudioPCMBuffer) {
        let n: Int
        var inputF: [Float]

        if let ch = buffer.floatChannelData {
            n = Int(buffer.frameLength)
            inputF = Array(UnsafeBufferPointer(start: ch[0], count: n))
        } else if let ch = buffer.int16ChannelData {
            n = Int(buffer.frameLength)
            inputF = (0..<n).map { Float(ch[0][$0]) / 32768.0 }
        } else {
            return
        }

        audioQueue.async { [weak self] in
            guard let s = self else { return }
            s.processInputSamples(inputF, count: n)
        }
    }

    private func processInputSamples(_ inputF: [Float], count n: Int) {
        // Feed detection ring buffer
        for i in 0..<n {
            detectRing[detectPos] = inputF[i]
            detectPos = (detectPos + 1) % detectBufSize
        }

        let hz = detector.detect(ring: detectRing, writePos: detectPos)
        var ratio = 1.0
        uiCounter += 1
        let postUI = (uiCounter % 4 == 0)

        if hz > 0, let maqam = activeMaqam, !bypassMode {
            voiceWasActive = true
            let target = maqam.nearestNote(hz: hz, rootHz: rootHz)
            let raw = target > 0 ? target / hz : 1.0
            let desired = 1.0 + (raw - 1.0) * sensitivity
            currentRatio += (desired - currentRatio) * correctionSpeed
            ratio = currentRatio

            if postUI {
                let dev = maqam.deviationCents(hz: hz, rootHz: rootHz)
                let capturedHz = hz
                let capturedTarget = target
                let capturedDev = dev
                DispatchQueue.main.async { [weak self] in
                    self?.delegate?.onPitchDetected(detectedHz: capturedHz,
                                                   targetHz: capturedTarget,
                                                   deviationCents: capturedDev)
                }
            }
        } else if hz <= 0 {
            if voiceWasActive {
                voiceWasActive = false
                shifter.reset()
            }
            currentRatio = 1.0
            ratio = 1.0
            if postUI {
                DispatchQueue.main.async { [weak self] in
                    self?.delegate?.onSilence()
                }
            }
        } else {
            // Bypass mode or no maqam — pass through at ratio 1.0
            ratio = 1.0
            currentRatio = 1.0
        }

        var outputF = [Float](repeating: 0, count: n)
        shifter.process(input: inputF, output: &outputF, ratio: ratio)

        // Write to output ring buffer
        for i in 0..<n {
            outBuf[(outWrPos + i) % outBuf.count] = outputF[i]
        }
        outWrPos = (outWrPos + n) % outBuf.count
        outFill = min(outFill + n, outBuf.count)

        // Write to recording if active
        if writer.isActive {
            writer.write(samples: outputF)
        }
    }
}
