import UIKit
import AVFoundation

// MARK: - PitchMeterView

final class PitchMeterView: UIView {

    /// Current deviation in cents, range –100…+100
    var deviationCents: Double = 0 {
        didSet { setNeedsDisplay() }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor.systemGray6
        layer.cornerRadius = 6
        layer.masksToBounds = true
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        backgroundColor = UIColor.systemGray6
        layer.cornerRadius = 6
        layer.masksToBounds = true
    }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }

        let midX = rect.midX
        let h = rect.height

        // Background
        UIColor.systemGray6.setFill()
        ctx.fill(rect)

        // Determine bar color
        let absCents = abs(deviationCents)
        let barColor: UIColor
        if absCents < 15 {
            barColor = .systemGreen
        } else if absCents < 40 {
            barColor = .systemYellow
        } else {
            barColor = .systemRed
        }

        // Clamped ratio 0…1 relative to ±100 cents
        let ratio = CGFloat(min(abs(deviationCents), 100.0) / 100.0)
        let barW = (rect.width / 2) * ratio

        barColor.setFill()
        if deviationCents >= 0 {
            // Bar extends right from center
            let barRect = CGRect(x: midX, y: 2, width: barW, height: h - 4)
            ctx.fill(barRect)
        } else {
            // Bar extends left from center
            let barRect = CGRect(x: midX - barW, y: 2, width: barW, height: h - 4)
            ctx.fill(barRect)
        }

        // Center line
        UIColor.label.setFill()
        ctx.fill(CGRect(x: midX - 1, y: 0, width: 2, height: h))

        // Tick marks at ±25, ±50, ±75 cents
        UIColor.systemGray3.setFill()
        for tick in [25.0, 50.0, 75.0] {
            let offset = CGFloat(tick / 100.0) * (rect.width / 2)
            ctx.fill(CGRect(x: midX + offset - 0.5, y: h * 0.25, width: 1, height: h * 0.5))
            ctx.fill(CGRect(x: midX - offset - 0.5, y: h * 0.25, width: 1, height: h * 0.5))
        }
    }
}

// MARK: - PerformanceViewController

final class PerformanceViewController: UIViewController {

    // MARK: UI Elements

    private let titleLabel: UILabel = {
        let l = UILabel()
        l.text = "أوتوتيون عربي"
        l.font = UIFont.boldSystemFont(ofSize: 28)
        l.textColor = .systemOrange
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let statusLabel: UILabel = {
        let l = UILabel()
        l.text = "● متوقف"
        l.font = UIFont.systemFont(ofSize: 16)
        l.textColor = .systemGray
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let detectedNoteLabel: UILabel = {
        let l = UILabel()
        l.text = "—"
        l.font = UIFont.boldSystemFont(ofSize: 48)
        l.textColor = .label
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let detectedHzLabel: UILabel = {
        let l = UILabel()
        l.text = "— هرتز"
        l.font = UIFont.systemFont(ofSize: 14)
        l.textColor = .secondaryLabel
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let arrowLabel: UILabel = {
        let l = UILabel()
        l.text = "←"
        l.font = UIFont.systemFont(ofSize: 24)
        l.textColor = .systemGray
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let targetNoteLabel: UILabel = {
        let l = UILabel()
        l.text = "—"
        l.font = UIFont.boldSystemFont(ofSize: 48)
        l.textColor = .systemGreen
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let deviationLabel: UILabel = {
        let l = UILabel()
        l.text = "±0 سنت"
        l.font = UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .medium)
        l.textColor = .secondaryLabel
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let pitchMeter: PitchMeterView = {
        let v = PitchMeterView()
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private let startButton: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("بدء", for: .normal)
        b.titleLabel?.font = UIFont.boldSystemFont(ofSize: 20)
        b.backgroundColor = .systemOrange
        b.setTitleColor(.white, for: .normal)
        b.layer.cornerRadius = 14
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    private let recordButton: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("● تسجيل", for: .normal)
        b.titleLabel?.font = UIFont.boldSystemFont(ofSize: 18)
        b.backgroundColor = .systemRed
        b.setTitleColor(.white, for: .normal)
        b.layer.cornerRadius = 14
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    // MARK: State

    private var isEngineRunning = false

    // MARK: Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = "أداء"
        setupLayout()
        startButton.addTarget(self, action: #selector(startButtonTapped), for: .touchUpInside)
        recordButton.addTarget(self, action: #selector(recordButtonTapped), for: .touchUpInside)
        AudioEngine.shared.delegate = self
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        AudioEngine.shared.delegate = self
    }

    // MARK: Layout

    private func setupLayout() {
        // Notes row stack
        let notesRow = UIStackView(arrangedSubviews: [detectedNoteLabel, arrowLabel, targetNoteLabel])
        notesRow.axis = .horizontal
        notesRow.alignment = .center
        notesRow.distribution = .equalCentering
        notesRow.spacing = 12
        notesRow.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, statusLabel, notesRow, detectedHzLabel,
         deviationLabel, pitchMeter, startButton, recordButton].forEach {
            view.addSubview($0)
        }

        let safeArea = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            // Title
            titleLabel.topAnchor.constraint(equalTo: safeArea.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Status
            statusLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Notes row
            notesRow.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 32),
            notesRow.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            notesRow.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            // Hz label
            detectedHzLabel.topAnchor.constraint(equalTo: notesRow.bottomAnchor, constant: 8),
            detectedHzLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            detectedHzLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Deviation label
            deviationLabel.topAnchor.constraint(equalTo: detectedHzLabel.bottomAnchor, constant: 12),
            deviationLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            deviationLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Pitch meter
            pitchMeter.topAnchor.constraint(equalTo: deviationLabel.bottomAnchor, constant: 16),
            pitchMeter.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            pitchMeter.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            pitchMeter.heightAnchor.constraint(equalToConstant: 40),

            // Start button
            startButton.topAnchor.constraint(equalTo: pitchMeter.bottomAnchor, constant: 40),
            startButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            startButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            startButton.heightAnchor.constraint(equalToConstant: 56),

            // Record button
            recordButton.topAnchor.constraint(equalTo: startButton.bottomAnchor, constant: 16),
            recordButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            recordButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            recordButton.heightAnchor.constraint(equalToConstant: 52),
        ])
    }

    // MARK: Actions

    @objc private func startButtonTapped() {
        if isEngineRunning {
            stopEngine()
        } else {
            checkMicPermissionAndStart()
        }
    }

    @objc private func recordButtonTapped() {
        guard isEngineRunning else {
            showAlert(title: "تنبيه", message: "يجب تشغيل المحرك أولاً")
            return
        }
        if AudioEngine.shared.isRecording {
            _ = AudioEngine.shared.stopRecording()
            recordButton.setTitle("● تسجيل", for: .normal)
            recordButton.backgroundColor = .systemRed
        } else {
            let saveDir = recordingsDirectory()
            let started = AudioEngine.shared.startRecording(saveDir: saveDir)
            if started {
                recordButton.setTitle("■ إيقاف التسجيل", for: .normal)
                recordButton.backgroundColor = .systemGray
            } else {
                showAlert(title: "خطأ", message: "تعذر بدء التسجيل")
            }
        }
    }

    // MARK: - Engine Control

    private func checkMicPermissionAndStart() {
        let status = AVAudioSession.sharedInstance().recordPermission
        switch status {
        case .granted:
            startEngine()
        case .denied:
            showAlert(title: "لا يوجد إذن", message: "يرجى السماح للتطبيق بالوصول إلى الميكروفون من الإعدادات")
        case .undetermined:
            AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.startEngine()
                    } else {
                        self?.showAlert(title: "مرفوض", message: "لا يمكن تشغيل الأوتوتيون بدون إذن الميكروفون")
                    }
                }
            }
        @unknown default:
            startEngine()
        }
    }

    private func startEngine() {
        do {
            try AudioEngine.shared.start()
            isEngineRunning = true
            startButton.setTitle("إيقاف", for: .normal)
            startButton.backgroundColor = .systemGray
            statusLabel.text = "● يعمل"
            statusLabel.textColor = .systemGreen
        } catch {
            showAlert(title: "خطأ في المحرك", message: error.localizedDescription)
        }
    }

    private func stopEngine() {
        if AudioEngine.shared.isRecording {
            _ = AudioEngine.shared.stopRecording()
            recordButton.setTitle("● تسجيل", for: .normal)
            recordButton.backgroundColor = .systemRed
        }
        AudioEngine.shared.stop()
        isEngineRunning = false
        startButton.setTitle("بدء", for: .normal)
        startButton.backgroundColor = .systemOrange
        statusLabel.text = "● متوقف"
        statusLabel.textColor = .systemGray
        resetDisplayToIdle()
    }

    // MARK: - Helpers

    private func resetDisplayToIdle() {
        detectedNoteLabel.text = "—"
        detectedHzLabel.text = "— هرتز"
        targetNoteLabel.text = "—"
        deviationLabel.text = "±0 سنت"
        pitchMeter.deviationCents = 0
    }

    private func hzToNoteName(_ hz: Double) -> String {
        guard hz > 0 else { return "—" }
        let noteNames = ["لا", "لا#", "سي", "دو", "دو#", "ري", "ري#", "مي", "فا", "فا#", "صول", "صول#"]
        let semitone = Int(round(12.0 * log2(hz / 440.0))) % 12
        let idx = ((semitone % 12) + 12) % 12
        return noteNames[idx]
    }

    private func recordingsDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("recordings", isDirectory: true)
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "حسناً", style: .default, handler: nil))
        present(alert, animated: true, completion: nil)
    }
}

// MARK: - AudioEngineDelegate

extension PerformanceViewController: AudioEngineDelegate {

    func onPitchDetected(detectedHz: Double, targetHz: Double, deviationCents: Double) {
        detectedNoteLabel.text = hzToNoteName(detectedHz)
        detectedHzLabel.text = String(format: "%.1f هرتز", detectedHz)
        targetNoteLabel.text = hzToNoteName(targetHz)

        let sign = deviationCents >= 0 ? "+" : ""
        deviationLabel.text = "\(sign)\(Int(round(deviationCents))) سنت"
        pitchMeter.deviationCents = deviationCents
    }

    func onSilence() {
        resetDisplayToIdle()
    }

    func onError(_ message: String) {
        showAlert(title: "خطأ", message: message)
    }

    func onRecordingSaved(url: URL, durationMs: Int64) {
        recordButton.setTitle("● تسجيل", for: .normal)
        recordButton.backgroundColor = .systemRed

        // Notify RecordingsVC via notification
        NotificationCenter.default.post(name: Notification.Name("RecordingSaved"),
                                        object: nil,
                                        userInfo: ["url": url, "durationMs": durationMs])

        let seconds = durationMs / 1000
        showAlert(title: "تم حفظ التسجيل",
                  message: String(format: "مدة التسجيل: %d:%02d", seconds / 60, seconds % 60))
    }
}
