import Foundation

final class PitchShifter {
    private static let fftSize = 2048
    private static let hopSize = 512
    private static let bins = 1025      // fftSize/2 + 1
    private static let olaSize = 8192
    private static let scale: Double = 2.0 / 3.0

    private let sampleRate: Double

    // Input ring buffer
    private var inRing: [Double]
    private var inWrPos: Int = 0
    private var inHopCnt: Int = 0

    // OLA output buffer
    private var outOLA: [Double]
    private var outRdPos: Int = 0
    private var outFill: Int = 0

    // Phase tracking
    private var anaPhase: [Double]
    private var synPhase: [Double]

    // Work arrays
    private var re: [Double]
    private var im: [Double]
    private var mag: [Double]
    private var truFreq: [Double]
    private var synMag: [Double]
    private var synFreq: [Double]

    // Hann window
    private var hann: [Double]

    init(sampleRate: Double) {
        self.sampleRate = sampleRate
        let n = PitchShifter.fftSize
        let b = PitchShifter.bins
        let o = PitchShifter.olaSize

        inRing   = [Double](repeating: 0.0, count: n)
        outOLA   = [Double](repeating: 0.0, count: o)
        anaPhase = [Double](repeating: 0.0, count: b)
        synPhase = [Double](repeating: 0.0, count: b)
        re       = [Double](repeating: 0.0, count: n)
        im       = [Double](repeating: 0.0, count: n)
        mag      = [Double](repeating: 0.0, count: b)
        truFreq  = [Double](repeating: 0.0, count: b)
        synMag   = [Double](repeating: 0.0, count: b)
        synFreq  = [Double](repeating: 0.0, count: b)

        hann = [Double](repeating: 0.0, count: n)
        for i in 0..<n {
            hann[i] = 0.5 * (1.0 - cos(2.0 * Double.pi * Double(i) / Double(n - 1)))
        }
    }

    func reset() {
        let n = PitchShifter.fftSize
        let b = PitchShifter.bins
        let o = PitchShifter.olaSize
        for i in 0..<n { inRing[i] = 0.0; re[i] = 0.0; im[i] = 0.0 }
        for i in 0..<b { anaPhase[i] = 0.0; synPhase[i] = 0.0; mag[i] = 0.0; truFreq[i] = 0.0; synMag[i] = 0.0; synFreq[i] = 0.0 }
        for i in 0..<o { outOLA[i] = 0.0 }
        inWrPos = 0; inHopCnt = 0; outRdPos = 0; outFill = 0
    }

    func process(input: [Float], output: inout [Float], ratio: Double) {
        let n = input.count
        let fftN = PitchShifter.fftSize
        let hop  = PitchShifter.hopSize
        let b    = PitchShifter.bins
        let olaLen = PitchShifter.olaSize

        for i in 0..<n {
            // Write sample into input ring
            inRing[inWrPos] = Double(input[i])
            inWrPos = (inWrPos + 1) % fftN
            inHopCnt += 1

            // When we have accumulated a full hop, process one frame
            if inHopCnt >= hop {
                inHopCnt = 0

                // Fill re/im from ring (oldest first)
                for k in 0..<fftN {
                    let idx = (inWrPos + k) % fftN
                    re[k] = inRing[idx] * hann[k]
                    im[k] = 0.0
                }

                // Forward FFT
                fftForward(re: &re, im: &im, n: fftN)

                // Analysis: compute magnitude and true frequency
                let omegaFactor = 2.0 * Double.pi * Double(hop) / Double(fftN)
                for k in 0..<b {
                    let r = re[k], imag = im[k]
                    let m = sqrt(r * r + imag * imag)
                    let phase = atan2(imag, r)
                    var delta = phase - anaPhase[k] - omegaFactor * Double(k)
                    // Wrap delta to [-pi, pi]
                    delta -= 2.0 * Double.pi * round(delta / (2.0 * Double.pi))
                    let tf = (omegaFactor * Double(k) + delta) * Double(fftN) / Double(hop)
                    anaPhase[k] = phase
                    mag[k] = m
                    truFreq[k] = tf / (2.0 * Double.pi)
                }

                // Synthesis: pitch-shift by ratio
                for k in 0..<b { synMag[k] = 0.0; synFreq[k] = 0.0 }
                for k in 0..<b {
                    let kNew = Int(Double(k) * ratio)
                    if kNew < b {
                        synMag[kNew] += mag[k]
                        synFreq[kNew] = truFreq[k] * ratio
                    }
                }

                // Update synthesis phases and reconstruct spectrum
                let twoPiHopOverN = 2.0 * Double.pi * Double(hop) / Double(fftN)
                for k in 0..<b {
                    synPhase[k] += twoPiHopOverN * synFreq[k] * Double(fftN)
                    re[k] = synMag[k] * cos(synPhase[k])
                    im[k] = synMag[k] * sin(synPhase[k])
                }

                // Mirror spectrum for real IFFT
                for k in 1..<(fftN / 2) {
                    re[fftN - k] =  re[k]
                    im[fftN - k] = -im[k]
                }

                // Inverse FFT
                fftInverse(re: &re, im: &im, n: fftN)

                // OLA with hann window and scale
                for k in 0..<fftN {
                    let pos = (outRdPos + outFill + k) % olaLen
                    outOLA[pos] += re[k] * hann[k] * PitchShifter.scale
                }
                outFill += hop
                if outFill > olaLen { outFill = olaLen }
            }

            // Read one sample from OLA output
            if outFill > 0 {
                output[i] = Float(outOLA[outRdPos])
                outOLA[outRdPos] = 0.0
                outRdPos = (outRdPos + 1) % olaLen
                outFill -= 1
            } else {
                output[i] = 0.0
            }
        }
    }

    // MARK: - Cooley-Tukey FFT

    private func fftForward(re: inout [Double], im: inout [Double], n: Int) {
        // Bit reversal
        var j = 0
        for i in 1..<n {
            var bit = n >> 1
            while (j & bit) != 0 {
                j ^= bit
                bit >>= 1
            }
            j ^= bit
            if i < j {
                re.swapAt(i, j)
                im.swapAt(i, j)
            }
        }

        // Butterfly stages
        var len = 2
        while len <= n {
            let ang = -2.0 * Double.pi / Double(len)
            let wRe = cos(ang)
            let wIm = sin(ang)
            var i = 0
            while i < n {
                var curRe = 1.0
                var curIm = 0.0
                for k in 0..<(len / 2) {
                    let uRe = re[i + k]
                    let uIm = im[i + k]
                    let vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    let vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    let nr = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nr
                }
                i += len
            }
            len <<= 1
        }
    }

    private func fftInverse(re: inout [Double], im: inout [Double], n: Int) {
        // Conjugate
        for i in 0..<n { im[i] = -im[i] }
        // Forward FFT
        fftForward(re: &re, im: &im, n: n)
        // Conjugate and scale
        let scale = 1.0 / Double(n)
        for i in 0..<n {
            re[i] =  re[i] * scale
            im[i] = -im[i] * scale
        }
    }
}
