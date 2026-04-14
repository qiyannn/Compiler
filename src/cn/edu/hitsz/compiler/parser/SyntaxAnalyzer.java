package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Action;
import cn.edu.hitsz.compiler.parser.table.LRTable;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

//TODO: 实验二: 实现 LR 语法分析驱动程序

/**
 * LR 语法分析驱动程序
 * <br>
 * 该程序接受词法单元串与 LR 分析表 (action 和 goto 表), 按表对词法单元流进行分析, 执行对应动作, 并在执行动作时通知各注册的观察者.
 * <br>
 * 你应当按照被挖空的方法的文档实现对应方法, 你可以随意为该类添加你需要的私有成员对象, 但不应该再为此类添加公有接口, 也不应该改动未被挖空的方法,
 * 除非你已经同助教充分沟通, 并能证明你的修改的合理性, 且令助教确定可能被改动的评测方法. 随意修改该类的其它部分有可能导致自动评测出错而被扣分.
 */
public class SyntaxAnalyzer {
    private final SymbolTable symbolTable;
    private final List<ActionObserver> observers = new ArrayList<>();
    private final Queue<Token> tokens = new LinkedList<>();
    private LRTable lrTable;


    public SyntaxAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 注册新的观察者
     *
     * @param observer 观察者
     */
    public void registerObserver(ActionObserver observer) {
        observers.add(observer);
        observer.setSymbolTable(symbolTable);
    }

    /**
     * 在执行 shift 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param currentToken  当前词法单元
     */
    public void callWhenInShift(Status currentStatus, Token currentToken) {
        for (final var listener : observers) {
            listener.whenShift(currentStatus, currentToken);
        }
    }

    /**
     * 在执行 reduce 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param production    待规约的产生式
     */
    public void callWhenInReduce(Status currentStatus, Production production) {
        for (final var listener : observers) {
            listener.whenReduce(currentStatus, production);
        }
    }

    /**
     * 在执行 accept 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     */
    public void callWhenInAccept(Status currentStatus) {
        for (final var listener : observers) {
            listener.whenAccept(currentStatus);
        }
    }

    public void loadTokens(Iterable<Token> tokens) {
        // 加载词法单元
        for (Token token : tokens) {
            this.tokens.offer(token);
        }
    }

    public void loadLRTable(LRTable table) {
        // 加载 LR 分析表
        this.lrTable = table;
    }

    public void run() {
        // 实现驱动程序
        Stack<Status> statusStack = new Stack<>();
        statusStack.push(lrTable.getInit());

        while (!tokens.isEmpty()) {
            Token currentToken = tokens.peek();
            Status currentStatus = statusStack.peek();
            Action action = lrTable.getAction(currentStatus, currentToken);

            if (action.getKind() == Action.ActionKind.Shift) {
                callWhenInShift(currentStatus, currentToken);
                statusStack.push(action.getStatus());
                tokens.poll(); // 消耗当前 token
            } else if (action.getKind() == Action.ActionKind.Reduce) {
                Production production = action.getProduction();
                callWhenInReduce(currentStatus, production);
                
                // 规约：弹出产生式体长度个状态
                int popCount = production.body().size();
                for (int i = 0; i < popCount; i++) {
                    statusStack.pop();
                }
                
                // 将 GOTO 状态压栈
                Status nextStatus = lrTable.getGoto(statusStack.peek(), production.head());
                statusStack.push(nextStatus);
            } else if (action.getKind() == Action.ActionKind.Accept) {
                callWhenInAccept(currentStatus);
                break;
            } else {
                throw new RuntimeException("Syntax Error at token: " + currentToken);
            }
        }
    }
}
