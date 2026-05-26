import UIKit
import AVFoundation

// MARK: - RecordingItem

private struct RecordingItem {
    let url: URL
    let filename: String
    let fileSize: Int64
    let date: Date
}

// MARK: - RecordingCell

private final class RecordingCell: UITableViewCell {

    static let reuseID = "RecordingCell"

    private let filenameLabel: UILabel = {
        let l = UILabel()
        l.font = UIFont.systemFont(ofSize: 15, weight: .medium)
        l.textColor = .label
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let sizeLabel: UILabel = {
        let l = UILabel()
        l.font = UIFont.systemFont(ofSize: 12)
        l.textColor = .secondaryLabel
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    let playButton: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("▶", for: .normal)
        b.titleLabel?.font = UIFont.systemFont(ofSize: 18)
        b.tintColor = .systemGreen
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    let shareButton: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("↑", for: .normal)
        b.titleLabel?.font = UIFont.systemFont(ofSize: 18)
        b.tintColor = .systemBlue
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    let deleteButton: UIButton = {
        let b = UIButton(type: .system)
        b.setTitle("✕", for: .normal)
        b.titleLabel?.font = UIFont.systemFont(ofSize: 18)
        b.tintColor = .systemRed
        b.translatesAutoresizingMaskIntoConstraints = false
        return b
    }()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupLayout()
        selectionStyle = .none
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    private func setupLayout() {
        let labelStack = UIStackView(arrangedSubviews: [filenameLabel, sizeLabel])
        labelStack.axis = .vertical
        labelStack.spacing = 2
        labelStack.translatesAutoresizingMaskIntoConstraints = false

        let buttonStack = UIStackView(arrangedSubviews: [playButton, shareButton, deleteButton])
        buttonStack.axis = .horizontal
        buttonStack.spacing = 12
        buttonStack.alignment = .center
        buttonStack.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(labelStack)
        contentView.addSubview(buttonStack)

        NSLayoutConstraint.activate([
            buttonStack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            buttonStack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),

            labelStack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            labelStack.leadingAnchor.constraint(equalTo: buttonStack.trailingAnchor, constant: 12),
            labelStack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            labelStack.topAnchor.constraint(greaterThanOrEqualTo: contentView.topAnchor, constant: 8),
            labelStack.bottomAnchor.constraint(lessThanOrEqualTo: contentView.bottomAnchor, constant: -8),
        ])
    }

    func configure(item: RecordingItem) {
        filenameLabel.text = item.filename
        let kb = Double(item.fileSize) / 1024.0
        if kb >= 1024 {
            sizeLabel.text = String(format: "%.1f MB", kb / 1024.0)
        } else {
            sizeLabel.text = String(format: "%.0f KB", kb)
        }
    }
}

// MARK: - RecordingsViewController

final class RecordingsViewController: UIViewController {

    // MARK: - Properties

    private var recordings: [RecordingItem] = []
    private var audioPlayer: AVAudioPlayer?
    private var playingIndexPath: IndexPath?

    private let tableView: UITableView = {
        let tv = UITableView(frame: .zero, style: .plain)
        tv.translatesAutoresizingMaskIntoConstraints = false
        return tv
    }()

    private let emptyLabel: UILabel = {
        let l = UILabel()
        l.text = "لا توجد تسجيلات"
        l.font = UIFont.systemFont(ofSize: 18)
        l.textColor = .secondaryLabel
        l.textAlignment = .center
        l.translatesAutoresizingMaskIntoConstraints = false
        l.isHidden = true
        return l
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = "تسجيلات"

        setupTableView()
        setupLayout()
        loadRecordings()
        registerForNotifications()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Setup

    private func setupTableView() {
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(RecordingCell.self, forCellReuseIdentifier: RecordingCell.reuseID)
        tableView.rowHeight = 64
        tableView.estimatedRowHeight = 64
    }

    private func setupLayout() {
        view.addSubview(tableView)
        view.addSubview(emptyLabel)

        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: safe.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    private func registerForNotifications() {
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(onRecordingSaved(_:)),
                                               name: Notification.Name("RecordingSaved"),
                                               object: nil)
    }

    // MARK: - Data

    private func recordingsDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("recordings", isDirectory: true)
    }

    private func loadRecordings() {
        let dir = recordingsDirectory()

        // Also check music directory as fallback
        var urls: [URL] = []
        let fm = FileManager.default

        if fm.fileExists(atPath: dir.path) {
            urls += (try? fm.contentsOfDirectory(at: dir,
                                                  includingPropertiesForKeys: [.fileSizeKey, .creationDateKey],
                                                  options: [.skipsHiddenFiles])) ?? []
        }

        let musicURLs = fm.urls(for: .musicDirectory, in: .userDomainMask)
        if let musicDir = musicURLs.first {
            let musicItems = (try? fm.contentsOfDirectory(at: musicDir,
                                                          includingPropertiesForKeys: [.fileSizeKey, .creationDateKey],
                                                          options: [.skipsHiddenFiles])) ?? []
            urls += musicItems
        }

        // Filter WAV files from this app (filename starts with "AutoTune_")
        recordings = urls
            .filter { $0.pathExtension.lowercased() == "wav" && $0.lastPathComponent.hasPrefix("AutoTune_") }
            .compactMap { url -> RecordingItem? in
                let attrs = try? fm.attributesOfItem(atPath: url.path)
                let size = (attrs?[.size] as? Int64) ?? 0
                let date = (attrs?[.creationDate] as? Date) ?? Date()
                return RecordingItem(url: url,
                                     filename: url.lastPathComponent,
                                     fileSize: size,
                                     date: date)
            }
            .sorted { $0.date > $1.date }

        tableView.reloadData()
        emptyLabel.isHidden = !recordings.isEmpty
    }

    // MARK: - Notifications

    @objc private func onRecordingSaved(_ notification: Notification) {
        loadRecordings()
    }

    // MARK: - Playback

    @objc private func playButtonTapped(_ sender: UIButton) {
        // Find the cell that owns this button
        var view: UIView? = sender
        while view != nil, !(view is UITableViewCell) {
            view = view?.superview
        }
        guard let cell = view as? UITableViewCell,
              let indexPath = tableView.indexPath(for: cell) else { return }

        let item = recordings[indexPath.row]

        if let playing = playingIndexPath, playing == indexPath {
            // Stop current playback
            audioPlayer?.stop()
            audioPlayer = nil
            playingIndexPath = nil
            sender.setTitle("▶", for: .normal)
            sender.tintColor = .systemGreen
        } else {
            // Stop previous if any
            if let prevPath = playingIndexPath,
               let prevCell = tableView.cellForRow(at: prevPath) as? RecordingCell {
                audioPlayer?.stop()
                prevCell.playButton.setTitle("▶", for: .normal)
                prevCell.playButton.tintColor = .systemGreen
            }
            audioPlayer = nil

            // Start new
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback)
                try AVAudioSession.sharedInstance().setActive(true)
                let player = try AVAudioPlayer(contentsOf: item.url)
                player.delegate = self
                player.play()
                audioPlayer = player
                playingIndexPath = indexPath
                sender.setTitle("■", for: .normal)
                sender.tintColor = .systemRed
            } catch {
                showAlert(title: "خطأ", message: "تعذر تشغيل الملف: \(error.localizedDescription)")
            }
        }
    }

    @objc private func shareButtonTapped(_ sender: UIButton) {
        var view: UIView? = sender
        while view != nil, !(view is UITableViewCell) {
            view = view?.superview
        }
        guard let cell = view as? UITableViewCell,
              let indexPath = tableView.indexPath(for: cell) else { return }

        let item = recordings[indexPath.row]
        let activityVC = UIActivityViewController(activityItems: [item.url], applicationActivities: nil)

        // iPad popover support
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = sender
            popover.sourceRect = sender.bounds
        }

        present(activityVC, animated: true, completion: nil)
    }

    @objc private func deleteButtonTapped(_ sender: UIButton) {
        var view: UIView? = sender
        while view != nil, !(view is UITableViewCell) {
            view = view?.superview
        }
        guard let cell = view as? UITableViewCell,
              let indexPath = tableView.indexPath(for: cell) else { return }

        let item = recordings[indexPath.row]

        let alert = UIAlertController(title: "حذف التسجيل",
                                      message: "هل تريد حذف \(item.filename)؟",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "إلغاء", style: .cancel, handler: nil))
        alert.addAction(UIAlertAction(title: "حذف", style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            do {
                // Stop playback if playing this file
                if self.playingIndexPath == indexPath {
                    self.audioPlayer?.stop()
                    self.audioPlayer = nil
                    self.playingIndexPath = nil
                }
                try FileManager.default.removeItem(at: item.url)
                self.recordings.remove(at: indexPath.row)
                self.tableView.deleteRows(at: [indexPath], with: .automatic)
                self.emptyLabel.isHidden = !self.recordings.isEmpty
            } catch {
                self.showAlert(title: "خطأ", message: "تعذر حذف الملف: \(error.localizedDescription)")
            }
        })
        present(alert, animated: true, completion: nil)
    }

    // MARK: - Helpers

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "حسناً", style: .default, handler: nil))
        present(alert, animated: true, completion: nil)
    }
}

// MARK: - UITableViewDataSource

extension RecordingsViewController: UITableViewDataSource {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return recordings.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: RecordingCell.reuseID,
                                                 for: indexPath) as! RecordingCell
        cell.configure(item: recordings[indexPath.row])
        cell.playButton.addTarget(self, action: #selector(playButtonTapped(_:)), for: .touchUpInside)
        cell.shareButton.addTarget(self, action: #selector(shareButtonTapped(_:)), for: .touchUpInside)
        cell.deleteButton.addTarget(self, action: #selector(deleteButtonTapped(_:)), for: .touchUpInside)

        // Update play button state if this row is currently playing
        if playingIndexPath == indexPath {
            cell.playButton.setTitle("■", for: .normal)
            cell.playButton.tintColor = .systemRed
        } else {
            cell.playButton.setTitle("▶", for: .normal)
            cell.playButton.tintColor = .systemGreen
        }

        return cell
    }
}

// MARK: - UITableViewDelegate

extension RecordingsViewController: UITableViewDelegate {

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
    }
}

// MARK: - AVAudioPlayerDelegate

extension RecordingsViewController: AVAudioPlayerDelegate {

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        if let indexPath = playingIndexPath,
           let cell = tableView.cellForRow(at: indexPath) as? RecordingCell {
            cell.playButton.setTitle("▶", for: .normal)
            cell.playButton.tintColor = .systemGreen
        }
        audioPlayer = nil
        playingIndexPath = nil
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        audioPlayer = nil
        playingIndexPath = nil
        if let err = error {
            showAlert(title: "خطأ في التشغيل", message: err.localizedDescription)
        }
    }
}
