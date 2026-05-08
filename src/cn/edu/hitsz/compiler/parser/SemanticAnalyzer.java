package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private SymbolTable symbolTable;
    private final java.util.Stack<Node> stack = new java.util.Stack<>();

    private static final class Node {
        private final Token token;
        private final SourceCodeType type;
        private final java.util.List<String> ids;

        private Node(Token token, SourceCodeType type, java.util.List<String> ids) {
            this.token = token;
            this.type = type;
            this.ids = ids;
        }

        private static Node forToken(Token token) {
            return new Node(token, null, null);
        }

        private static Node forType(SourceCodeType type) {
            return new Node(null, type, null);
        }

        private static Node forIds(java.util.List<String> ids) {
            return new Node(null, null, ids);
        }

        private static Node empty() {
            return new Node(null, null, null);
        }
    }

    private static boolean isProduction(Production production, String head, String... body) {
        if (!production.head().getTermName().equals(head)) {
            return false;
        }
        final var prodBody = production.body();
        if (prodBody.size() != body.length) {
            return false;
        }
        for (int i = 0; i < body.length; i++) {
            if (!prodBody.get(i).getTermName().equals(body[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void whenAccept(Status currentStatus) {
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final int count = production.body().size();
        final var rhs = new java.util.ArrayList<Node>(count);
        for (int i = 0; i < count; i++) {
            rhs.add(stack.pop());
        }
        java.util.Collections.reverse(rhs);

        if (isProduction(production, "D", "int")) {
            stack.push(Node.forType(SourceCodeType.Int));
            return;
        }

        if (isProduction(production, "IdList", "id")) {
            final var id = rhs.get(0).token.getText();
            stack.push(Node.forIds(java.util.List.of(id)));
            return;
        }

        if (isProduction(production, "IdList", "id", ",", "IdList")) {
            final var id = rhs.get(0).token.getText();
            final var tail = rhs.get(2).ids == null ? java.util.List.<String>of() : rhs.get(2).ids;
            final var ids = new java.util.ArrayList<String>(1 + tail.size());
            ids.add(id);
            ids.addAll(tail);
            stack.push(Node.forIds(java.util.Collections.unmodifiableList(ids)));
            return;
        }

        if (isProduction(production, "S", "D", "IdList")) {
            final var type = rhs.get(0).type;
            final var ids = rhs.get(1).ids == null ? java.util.List.<String>of() : rhs.get(1).ids;
            for (final var id : ids) {
                if (!symbolTable.has(id)) {
                    symbolTable.add(id);
                }
                symbolTable.get(id).setType(type);
            }
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "S", "D", "id", "=", "E")) {
            final var type = rhs.get(0).type;
            final var id = rhs.get(1).token.getText();
            if (!symbolTable.has(id)) {
                symbolTable.add(id);
            }
            symbolTable.get(id).setType(type);
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "S", "id", "=", "E")) {
            final var id = rhs.get(0).token.getText();
            if (!symbolTable.has(id) || symbolTable.get(id).getType() == null) {
                throw new RuntimeException("Undeclared identifier: " + id);
            }
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "B", "id")) {
            final var id = rhs.get(0).token.getText();
            if (!symbolTable.has(id) || symbolTable.get(id).getType() == null) {
                throw new RuntimeException("Undeclared identifier: " + id);
            }
            stack.push(Node.empty());
            return;
        }

        stack.push(Node.empty());
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        stack.push(Node.forToken(currentToken));
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }
}
