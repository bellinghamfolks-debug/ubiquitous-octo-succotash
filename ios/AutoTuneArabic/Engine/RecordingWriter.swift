import Foundation
import AVFoundation

final class RecordingWriter {
    private var fileHandle: FileHandle?
    private var outputURL: URL?
    private var sampleCount: Int = 0
    static let sampleRate: Int32 = 44100

    var isActive: Bool { fileHandle != nil }

    func start(saveDir: URL) throws -> URL {
        let timestamp = Int(Date().timeIntervalSince1970)
        let filename = "AutoTune_\(timestamp).wav"
        let url = saveDir.appendingPathComponent(filename)

        // Create directory if needed
        try FileManager.default.createDirectory(at: saveDir, withIntermediateDirectories: true, attributes: nil)

        // Create the file
        FileManager.default.createFile(atPath: url.path, contents: nil, attributes: nil)
        let handle = try FileHandle(forWritingTo: url)

        // Write placeholder WAV header (44 bytes, sizes = 0 initially)
        var header = Data(capacity: 44)

        // RIFF chunk
        header.append(contentsOf: [0x52, 0x49, 0x46, 0x46]) // "RIFF"
        appendUInt32LE(&header, 0)                           // file size - 8 (placeholder)
        header.append(contentsOf: [0x57, 0x41, 0x56, 0x45]) // "WAVE"

        // fmt sub-chunk
        header.append(contentsOf: [0x66, 0x6D, 0x74, 0x20]) // "fmt "
        appendUInt32LE(&header, 16)                          // sub-chunk size = 16
        appendUInt16LE(&header, 1)                           // PCM = 1
        appendUInt16LE(&header, 1)                           // mono = 1
        appendUInt32LE(&header, UInt32(RecordingWriter.sampleRate)) // sample rate
        appendUInt32LE(&header, UInt32(RecordingWriter.sampleRate) * 2) // byte rate (sr * channels * bitsPerSample/8)
        appendUInt16LE(&header, 2)                           // block align (channels * bitsPerSample/8)
        appendUInt16LE(&header, 16)                          // bits per sample

        // data sub-chunk
        header.append(contentsOf: [0x64, 0x61, 0x74, 0x61]) // "data"
        appendUInt32LE(&header, 0)                           // data size (placeholder)

        handle.write(header)

        fileHandle = handle
        outputURL = url
        sampleCount = 0
        return url
    }

    func write(samples: [Float]) {
        guard let handle = fileHandle else { return }
        var data = Data(capacity: samples.count * 2)
        for sample in samples {
            let clamped = max(-1.0, min(1.0, sample))
            let int16Val = Int16(clamped * 32767.0)
            var le = int16Val.littleEndian
            withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
        }
        handle.write(data)
        sampleCount += samples.count
    }

    func stop() -> URL? {
        guard let handle = fileHandle, let url = outputURL else { return nil }

        let dataSize = UInt32(sampleCount * 2)          // 16-bit = 2 bytes per sample
        let fileSize = dataSize + 36                     // 44 byte header - 8 byte RIFF header

        // Patch file size at offset 4
        handle.seek(toFileOffset: 4)
        var fs = fileSize.littleEndian
        handle.write(Data(bytes: &fs, count: 4))

        // Patch data size at offset 40
        handle.seek(toFileOffset: 40)
        var ds = dataSize.littleEndian
        handle.write(Data(bytes: &ds, count: 4))

        handle.closeFile()
        fileHandle = nil
        outputURL = nil
        sampleCount = 0
        return url
    }

    // MARK: - Helpers

    private func appendUInt32LE(_ data: inout Data, _ value: UInt32) {
        var le = value.littleEndian
        withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
    }

    private func appendUInt16LE(_ data: inout Data, _ value: UInt16) {
        var le = value.littleEndian
        withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
    }
}
