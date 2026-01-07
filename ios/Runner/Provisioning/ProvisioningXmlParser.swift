import Foundation

struct ProvisioningConfig {
    let entries: [String: String]
    func get(_ key: String) -> String? { entries[key] }
}

final class ProvisioningXmlParser: NSObject, XMLParserDelegate {
    private var entries: [String: String] = [:]
    private var currentKey: String?
    private var currentValue: String = ""

    func parse(data: Data) -> ProvisioningConfig {
        entries.removeAll()
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.parse()
        return ProvisioningConfig(entries: entries)
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        if elementName == "entry" {
            currentKey = attributeDict["name"]
            currentValue = ""
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentValue.append(string)
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if elementName == "entry" {
            let key = currentKey ?? ""
            entries[key] = currentValue.trimmingCharacters(in: .whitespacesAndNewlines)
            currentKey = nil
            currentValue = ""
        }
    }
}
