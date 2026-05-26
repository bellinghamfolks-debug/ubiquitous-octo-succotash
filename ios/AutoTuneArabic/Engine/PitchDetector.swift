import Foundation

final class PitchDetector {
    static let noPitch: Double = -1.0
    private static let yinThreshold: Double = 0.15
    private static let minEnergy: Double = 1e-6

    private let sampleRate: Int
    private let bufferSize: Int
    private var yin: [Double]
    private var linear: [Float]

    init(sampleRate: Int, bufferSize: Int) {
        self.sampleRate = sampleRate
        self.bufferSize = bufferSize
        self.yin = [Double](repeating: 0.0, count: bufferSize / 2)
        self.linear = [Float](repeating: 0.0, count: bufferSize)
    }

    func detect(ring: [Float], writePos: Int) -> Double {
        // Copy ring buffer to linear array in chronological order
        let tail = bufferSize - writePos
        linear.withUnsafeMutableBufferPointer { linBuf in
            ring.withUnsafeBufferPointer { ringBuf in
                // Copy from writePos to end
                for i in 0..<tail {
                    linBuf[i] = ringBuf[writePos + i]
                }
                // Copy from start to writePos
                for i in 0..<writePos {
                    linBuf[tail + i] = ringBuf[i]
                }
            }
        }

        let half = bufferSize / 2

        // Energy check
        var energy: Double = 0.0
        for i in 0..<bufferSize {
            let s = Double(linear[i])
            energy += s * s
        }
        if energy / Double(bufferSize) < PitchDetector.minEnergy {
            return PitchDetector.noPitch
        }

        // Difference function
        for tau in 0..<half {
            yin[tau] = 0.0
            for j in 0..<half {
                let d = Double(linear[j]) - Double(linear[j + tau])
                yin[tau] += d * d
            }
        }

        // Cumulative mean normalized difference function
        yin[0] = 1.0
        var runSum: Double = 0.0
        for tau in 1..<half {
            runSum += yin[tau]
            yin[tau] = runSum > 0.0 ? yin[tau] * Double(tau) / runSum : 1.0
        }

        // Find minimum below threshold
        let tauMin = sampleRate / 1200
        let tauMax = min(half - 2, sampleRate / 80)
        var tauEst = -1
        var tau = tauMin
        while tau <= tauMax {
            if yin[tau] < PitchDetector.yinThreshold {
                while tau + 1 <= tauMax && yin[tau + 1] < yin[tau] {
                    tau += 1
                }
                tauEst = tau
                break
            }
            tau += 1
        }

        if tauEst == -1 {
            return PitchDetector.noPitch
        }

        // Parabolic interpolation
        let betterTau: Double
        if tauEst > 1 && tauEst < half - 1 {
            let s0 = yin[tauEst - 1]
            let s1 = yin[tauEst]
            let s2 = yin[tauEst + 1]
            let den = 2.0 * (2.0 * s1 - s0 - s2)
            betterTau = abs(den) > 1e-12 ? Double(tauEst) + (s2 - s0) / den : Double(tauEst)
        } else {
            betterTau = Double(tauEst)
        }

        return Double(sampleRate) / betterTau
    }
}
