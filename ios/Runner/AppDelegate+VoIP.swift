import Foundation
import PushKit
import UIKit

extension AppDelegate: PKPushRegistryDelegate {
    func setupVoIPPush() {
        let registry = PKPushRegistry(queue: .main)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
    }

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        guard type == .voIP else { return }
        // Send credentials to server if needed.
    }

    public func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else {
            completion()
            return
        }
        DispatchQueue.main.async {
            if let appDelegate = UIApplication.shared.delegate as? AppDelegate {
                appDelegate.handleVoipPush(payload: payload)
            }
            completion()
        }
    }

    func handleVoipPush(payload: PKPushPayload) {
        // Wake SIP stack and notify of incoming call.
        NotificationCenter.default.post(name: .init("IncomingVoipCall"), object: payload.dictionaryPayload)
    }
}
