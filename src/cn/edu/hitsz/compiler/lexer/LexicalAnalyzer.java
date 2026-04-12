package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private String sourceCode = "";
    private List<Token> tokens = null;
    private static final Set<String> KEYWORDS = new HashSet<>(Set.of("int", "return"));

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // TODO: 词法分析前的缓冲区实现
        
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        sourceCode = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // TODO: 自动机实现的词法分析过程
        final var result = new ArrayList<Token>();
        final int n = sourceCode.length();
        int i = 0;

        while (i < n) {
            final char c = sourceCode.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < n) {
                    final char cj = sourceCode.charAt(j);
                    if (Character.isLetterOrDigit(cj) || cj == '_') {
                        j++;
                    } else {
                        break;
                    }
                }
                final var word = sourceCode.substring(i, j);
                if (KEYWORDS.contains(word)) {
                    result.add(Token.simple(word));
                } else {
                    if (!symbolTable.has(word)) {
                        symbolTable.add(word);
                    }
                    result.add(Token.normal("id", word));
                }
                i = j;
                continue;
            }

            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && Character.isDigit(sourceCode.charAt(j))) {
                    j++;
                }
                final var number = sourceCode.substring(i, j);
                result.add(Token.normal("IntConst", number));
                i = j;
                continue;
            }

            switch (c) {
                case ';' -> result.add(Token.simple("Semicolon"));
                case '=', ',', '+', '-', '*', '/', '(', ')' -> result.add(Token.simple(String.valueOf(c)));
                default -> throw new RuntimeException("Illegal character: " + c);
            }
            i++;
        }

        result.add(Token.eof());
        tokens = result;
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // TODO: 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        if (tokens == null) {
            throw new IllegalStateException("run() must be called before getTokens()");
        }
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
