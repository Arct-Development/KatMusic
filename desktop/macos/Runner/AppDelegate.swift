import Cocoa
import FlutterMacOS

/// Forwards macOS custom-scheme opens into the Dart deep-link channel.
///
/// macOS can deliver the open event before Flutter has built its first window,
/// so links are queued briefly and flushed by MainFlutterWindow.awakeFromNib.
final class DeepLinkBridge {
  private static var pending: [String] = []
  private static var channel: FlutterMethodChannel?
  private static var dartReady = false

  static func attach(to controller: FlutterViewController) {
    channel = FlutterMethodChannel(
      name: "katmusic/deeplink",
      binaryMessenger: controller.engine.binaryMessenger
    )
    channel?.setMethodCallHandler { call, result in
      if call.method == "ready" {
        DeepLinkBridge.dartReady = true
        DeepLinkBridge.flush()
        result(nil)
      } else {
        result(FlutterMethodNotImplemented)
      }
    }
  }

  static func receive(_ url: URL) {
    let link = url.absoluteString
    guard link.lowercased().hasPrefix("katmusic://") else { return }
    guard let channel = channel, dartReady else {
      pending.append(link)
      return
    }
    channel.invokeMethod("link", arguments: link)
  }

  private static func flush() {
    guard dartReady, let channel = channel else { return }
    let queued = pending
    pending.removeAll()
    for link in queued {
      channel.invokeMethod("link", arguments: link)
    }
  }
}

@main
class AppDelegate: FlutterAppDelegate {
  func application(_ application: NSApplication, open urls: [URL]) {
    for url in urls {
      DeepLinkBridge.receive(url)
    }
  }

  func application(_ application: NSApplication, openFiles filenames: [String]) {
    for filename in filenames {
      if let url = URL(string: filename) {
        DeepLinkBridge.receive(url)
      }
    }
    application.reply(toOpenOrPrint: .success)
  }

  override func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
    return true
  }

  override func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
    return true
  }
}
