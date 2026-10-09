import SwiftUI
import UIKit
import AVFoundation
import RivetCore

private func assetSymbol(_ type: String?) -> String {
    switch (type ?? "").lowercased() {
    case "desktop": return "desktopcomputer"
    case "server": return "server.rack"
    case "firewall", "access point", "switch", "router": return "network"
    case "printer": return "printer"
    case "phone", "mobile phone", "tablet": return "iphone"
    case "camera": return "video"
    case "display": return "display"
    default: return "laptopcomputer"
    }
}

@MainActor struct AssetRow: View {
    let asset: AssetSummary
    var showClient = true
    var body: some View {
        ListRowCard {
            IconTile(systemName: assetSymbol(asset.type))
            VStack(alignment: .leading, spacing: 2) {
                Text(asset.tag ?? asset.name).font(.subheadline.weight(.semibold))
                let model = [asset.make, asset.model].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
                Text(model.isEmpty ? asset.name : model).font(.caption).foregroundColor(Palette.label2).multilineTextAlignment(.leading)
            }
            Spacer()
            if showClient, let c = asset.client, !c.isEmpty {
                Text(c).font(.caption2).foregroundColor(Palette.label2).multilineTextAlignment(.trailing).frame(maxWidth: 110, alignment: .trailing)
            }
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
        }
    }
}

@MainActor struct AssetsView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @StateObject private var loader = PagedLoader<AssetSummary>()
    @State private var search = ""
    @State private var types: [String] = []
    @State private var typeIndex = 0

    private var selectedType: String { typeIndex == 0 || typeIndex > types.count ? "" : types[typeIndex - 1] }
    private struct Key: Equatable { var search: String; var type: Int }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                SearchField(placeholder: "Search assets…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
                FilterChips(items: ["All"] + types, selection: $typeIndex).padding(.bottom, 8)
                let state = loader.state
                if let error = state.error, state.items.isEmpty {
                    ErrorStateView(message: error, retry: reload)
                } else if state.items.isEmpty && state.isRefreshing {
                    LoadingStateView()
                } else if state.items.isEmpty {
                    EmptyStateView(title: "No assets found", systemImage: "laptopcomputer")
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(state.items) { a in
                                NavigationLink(value: AppRoute.asset(a.id)) { AssetRow(asset: a) }.buttonStyle(.plain)
                                    .task { await loader.loadMoreIfNeeded(current: a, fetchPage) }
                            }
                            if state.isLoadingMore { ProgressView().padding() }
                            Color.clear.frame(height: 80)
                        }
                    }.refreshable { await reload() }
                }
            }
            FloatingButton(systemName: "qrcode.viewfinder", label: "Scan barcode") { router.push(.scan) }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar()
        .task { types = (try? await session.api?.assetTypes()) ?? [] }
        .task(id: Key(search: search, type: typeIndex)) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func fetchPage(_ page: Int) async throws -> Paged<AssetSummary> {
        guard let api = session.api else { throw RivetError.invalidURL }
        return try await api.assets(search: search, page: page, type: selectedType)
    }
    private func reload() async { await loader.reload(fetchPage) }
}

@MainActor struct AssetDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @StateObject private var loader = Loader<AssetDetail>()
    @State private var copied = false

    var body: some View {
        LoadView(loader: loader, retry: load) { a in
            ScrollView {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 14) {
                            IconTile(systemName: assetSymbol(a.type), size: 52)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.tag ?? a.name).font(.title3.weight(.bold))
                                if let t = a.type { Text(t).foregroundColor(Palette.brand) }
                            }
                        }
                        if let s = a.status { Pill(text: s, tone: .cyan) }
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("Details").font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(.bottom, 4)
                        row("Department", a.client); row("Make", a.make); row("Model", a.model); row("OS", a.os)
                        row("Location", [a.locationName, a.physicalLocation].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " – "))
                        row("Contact", a.contactName); row("Purchase Date", a.purchaseDate.map { RivetDate.dateOnly($0) })
                        row("Warranty Expires", a.warrantyExpire.map { RivetDate.dateOnly($0) })
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    if let serial = a.serial, !serial.isEmpty {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Serial Number").font(.caption).foregroundColor(Palette.label2)
                                Text(serial).font(.system(.body, design: .monospaced).weight(.semibold))
                            }
                            Spacer()
                            Button { UIPasteboard.general.string = serial; copied = true } label: {
                                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                            }.accessibilityLabel("Copy serial number")
                        }.card().padding(.horizontal, 16)
                    }
                    if let n = a.notes, !n.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Notes").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                            Text(HTMLText.plain(n))
                        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
                    }
                }.padding(.vertical, 8)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(loader.value.map { $0.tag ?? $0.name } ?? "Asset").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder private func row(_ key: String, _ value: String?) -> some View {
        if let value, !value.isEmpty { KeyValueRow(key: key, value: value) }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.asset(id) }
    }
}

// MARK: Barcode scanner

/// Scans a barcode / QR code and opens the asset whose tag or serial matches.
@MainActor struct ScannerView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @State private var permission: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var message: String?
    @State private var looking = false
    @State private var scanKey = 0

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch permission {
                case .authorized: ScannerPreview(onCode: handle).id(scanKey).ignoresSafeArea()
                case .notDetermined: LoadingStateView()
                default:
                    VStack(spacing: 12) {
                        EmptyStateView(title: "Camera access is needed to scan barcodes.", systemImage: "camera")
                        Button("Open Settings") { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
                    }
                }
            }
            if looking { ProgressView("Looking up…").padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12)).padding(.bottom, 40) }
            else if let message {
                VStack(spacing: 8) {
                    Text(message).font(.subheadline).multilineTextAlignment(.center)
                    Button("Scan again") { self.message = nil; scanKey += 1 }.buttonStyle(.borderedProminent).tint(Palette.brand)
                }.padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14)).padding(.bottom, 40)
            }
        }
        .navigationTitle("Scan").navigationBarTitleDisplayMode(.inline)
        .task {
            if permission == .notDetermined {
                _ = await AVCaptureDevice.requestAccess(for: .video)
                permission = AVCaptureDevice.authorizationStatus(for: .video)
            }
        }
    }

    private func handle(_ code: String) {
        guard !looking, message == nil, let api = session.api else { return }
        looking = true
        Task {
            defer { looking = false }
            do {
                let found = try await api.assets(search: code, page: 1).data
                if let exact = found.first(where: { $0.tag == code || $0.serial == code }) ?? (found.count == 1 ? found.first : nil) {
                    router.pop(); router.push(.asset(exact.id))
                } else {
                    message = found.isEmpty ? "No asset found for “\(code)”." : "\(found.count) assets match “\(code)”. Try a more specific code."
                }
            } catch { message = userMessage(for: error) }
        }
    }
}

private struct ScannerPreview: UIViewControllerRepresentable {
    var onCode: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerController {
        let c = ScannerController(); c.onCode = onCode; return c
    }
    func updateUIViewController(_ uiViewController: ScannerController, context: Context) {}
}

private final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    private let session = AVCaptureSession()
    private var delivered = false

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }   // e.g. the Simulator has no camera
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr, .code128, .code39, .code93, .ean13, .ean8, .upce, .dataMatrix, .pdf417]
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.bounds
        view.layer.addSublayer(preview)
        DispatchQueue.global(qos: .userInitiated).async { [session] in session.startRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        view.layer.sublayers?.first { $0 is AVCaptureVideoPreviewLayer }?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning { session.stopRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !delivered, let code = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue else { return }
        delivered = true
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onCode?(code)
    }
}
