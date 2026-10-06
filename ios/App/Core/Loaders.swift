import SwiftUI
import RivetCore

enum LoadState<T> {
    case idle, loading
    case loaded(T)
    case failed(String)
}

/// Loads one value and exposes it as idle / loading / loaded / failed. Existing content stays visible while refreshing.
@MainActor
final class Loader<T>: ObservableObject {
    @Published private(set) var state: LoadState<T> = .idle

    var value: T? { if case .loaded(let v) = state { return v }; return nil }

    func load(_ work: () async throws -> T) async {
        if value == nil { state = .loading }
        do { state = .loaded(try await work()) }
        catch is CancellationError { return }
        catch let e as URLError where e.code == .cancelled { return }
        catch { if value == nil { state = .failed(userMessage(for: error)) } }
    }
}

/// Paged list loader (page numbers start at 1). A reload keeps the old rows until the new first page arrives.
@MainActor
final class PagedLoader<T: Identifiable & Decodable>: ObservableObject where T.ID: Hashable {
    @Published private(set) var state = PagedState<T>()
    private var generation = 0

    func reload(_ fetch: (Int) async throws -> Paged<T>) async {
        generation += 1
        let gen = generation
        state.isRefreshing = true
        defer { if gen == generation { state.isRefreshing = false } }
        do {
            let page = try await fetch(1)
            guard gen == generation else { return }
            state.items = page.data; state.total = page.total; state.page = 1; state.error = nil
        } catch is CancellationError {
        } catch let e as URLError where e.code == .cancelled {
        } catch {
            guard gen == generation else { return }
            state.error = userMessage(for: error)
        }
    }

    func loadMoreIfNeeded(current item: T, _ fetch: (Int) async throws -> Paged<T>) async {
        guard state.hasMore, !state.isLoadingMore, !state.isRefreshing,
              let last = state.items.last, last.id == item.id else { return }
        let gen = generation
        state.isLoadingMore = true
        defer { state.isLoadingMore = false }
        do {
            let page = try await fetch(state.page + 1)
            guard gen == generation else { return }
            let known = Set(state.items.map(\.id))
            state.items += page.data.filter { !known.contains($0.id) }
            state.total = page.total; state.page += 1
        } catch {
            if gen == generation { state.error = userMessage(for: error) }
        }
    }
}
