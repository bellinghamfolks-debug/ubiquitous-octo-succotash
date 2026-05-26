import UIKit

struct Maqam {
    let nameAr: String
    let cents: [Double]  // cents from root, octave span
    let color: UIColor

    func nearestNote(hz: Double, rootHz: Double) -> Double {
        guard hz > 0, rootHz > 0 else { return hz }
        let centsIn = 1200.0 * log2(hz / rootHz)
        var minDist = Double.infinity
        var bestCents = 0.0
        for octave in -2...2 {
            for c in cents {
                let nc = c + Double(octave) * 1200.0
                let d = abs(centsIn - nc)
                if d < minDist { minDist = d; bestCents = nc }
            }
        }
        return rootHz * pow(2.0, bestCents / 1200.0)
    }

    func deviationCents(hz: Double, rootHz: Double) -> Double {
        let target = nearestNote(hz: hz, rootHz: rootHz)
        guard target > 0 else { return 0 }
        return 1200.0 * log2(hz / target)
    }
}
