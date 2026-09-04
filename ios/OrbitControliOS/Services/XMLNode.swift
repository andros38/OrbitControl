import Foundation

final class XMLNode {
    let name: String
    var text: String = ""
    var children: [XMLNode] = []

    init(name: String) {
        self.name = name
    }

    func child(named name: String) -> XMLNode? {
        children.first { $0.name.caseInsensitiveCompare(name) == .orderedSame }
    }

    func descendants(named name: String) -> [XMLNode] {
        var result: [XMLNode] = []
        if self.name.caseInsensitiveCompare(name) == .orderedSame { result.append(self) }
        for child in children {
            result.append(contentsOf: child.descendants(named: name))
        }
        return result
    }

    func firstValue(_ names: String...) -> String? {
        firstValue(names)
    }

    func firstValue(_ names: [String]) -> String? {
        let targets = Set(names.map { $0.lowercased() })
        if targets.contains(name.lowercased()) {
            let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty { return value }
        }
        for child in children {
            if let value = child.firstValue(names) { return value }
        }
        return nil
    }

    func allNodes() -> [XMLNode] {
        [self] + children.flatMap { $0.allNodes() }
    }
}

enum HuaweiXML {
    static func parse(_ text: String) -> XMLNode? {
        let parser = XMLParser(data: Data(text.utf8))
        let delegate = XMLTreeParser()
        parser.delegate = delegate
        return parser.parse() ? delegate.root : nil
    }

    static func errorCode(_ node: XMLNode?) -> String? {
        guard let node else { return nil }
        if node.name.caseInsensitiveCompare("error") == .orderedSame {
            return node.firstValue("code")
        }
        return node.descendants(named: "error").first?.firstValue("code")
    }

    static func responseNode(_ node: XMLNode?) -> XMLNode? {
        guard let node else { return nil }
        return node.name.caseInsensitiveCompare("response") == .orderedSame ? node : node.child(named: "response")
    }

    static func request(_ fields: [String: String]) -> String {
        let body = fields.map { "<\($0.key)>\(escape($0.value))</\($0.key)>" }.joined()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>\(body)</request>"
    }

    static func serialize(_ node: XMLNode) -> String {
        let body: String
        if node.children.isEmpty {
            body = escape(node.text)
        } else {
            body = node.children.map(serialize).joined()
        }
        return "<\(node.name)>\(body)</\(node.name)>"
    }

    private static func escape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }
}

private final class XMLTreeParser: NSObject, XMLParserDelegate {
    var root: XMLNode?
    private var stack: [XMLNode] = []

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let node = XMLNode(name: elementName)
        if let parent = stack.last {
            parent.children.append(node)
        } else {
            root = node
        }
        stack.append(node)
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        stack.last?.text += string
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        _ = stack.popLast()
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        stack.last?.text += String(decoding: CDATABlock, as: UTF8.self)
    }
}
