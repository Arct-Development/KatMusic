import Cocoa
import FlutterMacOS

class MainFlutterWindow: NSWindow {
  override func awakeFromNib() {
    let flutterViewController = FlutterViewController()
    let windowFrame = self.frame
    self.contentViewController = flutterViewController
    self.setFrame(windowFrame, display: true)

    // The browser delivers `katmusic://` URLs through NSOpen events rather
    // than process arguments. Attach the bridge as soon as the Flutter engine
    // exists so cold launches and already-running windows use the same Dart
    // deep-link stream.
    DeepLinkBridge.attach(to: flutterViewController)

    RegisterGeneratedPlugins(registry: flutterViewController)

    super.awakeFromNib()
  }
}
