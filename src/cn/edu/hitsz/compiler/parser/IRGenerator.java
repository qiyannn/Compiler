package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    private SymbolTable symbolTable;
    private final java.util.Stack<Node> stack = new java.util.Stack<>();
    private final List<Instruction> instructions = new ArrayList<>();

    private static final class Node {
        private final Token token;
        private final IRValue value;
        private final java.util.List<String> ids;

        private Node(Token token, IRValue value, java.util.List<String> ids) {
            this.token = token;
            this.value = value;
            this.ids = ids;
        }

        private static Node forToken(Token token) {
            return new Node(token, null, null);
        }

        private static Node forValue(IRValue value) {
            return new Node(null, value, null);
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
    public void whenShift(Status currentStatus, Token currentToken) {
        stack.push(Node.forToken(currentToken));
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final int count = production.body().size();
        final var rhs = new ArrayList<Node>(count);
        for (int i = 0; i < count; i++) {
            rhs.add(stack.pop());
        }
        java.util.Collections.reverse(rhs);

        if (isProduction(production, "B", "IntConst")) {
            final var imm = IRImmediate.of(Integer.parseInt(rhs.get(0).token.getText()));
            stack.push(Node.forValue(imm));
            return;
        }

        if (isProduction(production, "B", "id")) {
            stack.push(Node.forValue(IRVariable.named(rhs.get(0).token.getText())));
            return;
        }

        if (isProduction(production, "B", "(", "E", ")")) {
            stack.push(Node.forValue(rhs.get(1).value));
            return;
        }

        if (isProduction(production, "B", "-", "B")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createSub(temp, IRImmediate.of(0), rhs.get(1).value));
            stack.push(Node.forValue(temp));
            return;
        }

        if (isProduction(production, "A", "B")) {
            stack.push(Node.forValue(rhs.get(0).value));
            return;
        }

        if (isProduction(production, "A", "A", "*", "B")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMul(temp, rhs.get(0).value, rhs.get(2).value));
            stack.push(Node.forValue(temp));
            return;
        }

        if (isProduction(production, "A", "A", "/", "B")) {
            throw new RuntimeException("Division is not supported by current IR");
        }

        if (isProduction(production, "E", "A")) {
            stack.push(Node.forValue(rhs.get(0).value));
            return;
        }

        if (isProduction(production, "E", "E", "+", "A")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createAdd(temp, rhs.get(0).value, rhs.get(2).value));
            stack.push(Node.forValue(temp));
            return;
        }

        if (isProduction(production, "E", "E", "-", "A")) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createSub(temp, rhs.get(0).value, rhs.get(2).value));
            stack.push(Node.forValue(temp));
            return;
        }

        if (isProduction(production, "S", "id", "=", "E")) {
            final var target = IRVariable.named(rhs.get(0).token.getText());
            instructions.add(Instruction.createMov(target, rhs.get(2).value));
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "S", "D", "id", "=", "E")) {
            final var target = IRVariable.named(rhs.get(1).token.getText());
            instructions.add(Instruction.createMov(target, rhs.get(3).value));
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "S", "return", "E")) {
            instructions.add(Instruction.createRet(rhs.get(1).value));
            stack.push(Node.empty());
            return;
        }

        if (isProduction(production, "IdList", "id")) {
            stack.push(Node.forIds(java.util.List.of(rhs.get(0).token.getText())));
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

        stack.push(Node.empty());
    }


    @Override
    public void whenAccept(Status currentStatus) {
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }

    public List<Instruction> getIR() {
        return java.util.Collections.unmodifiableList(instructions);
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}
