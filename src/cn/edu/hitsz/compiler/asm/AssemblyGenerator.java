package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    private static final List<String> REG_POOL = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        this.instructions = normalize(originInstructions);
        collectVariablesAndAssignOffsets();
        calcLastUse();
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        asmLines.clear();
        var2reg.clear();
        reg2var.clear();
        dirty.clear();

        asmLines.add(".text");

        if (!varOffset.isEmpty()) {
            final int frameSize = alignTo(varOffset.size() * 4, 16);
            emitStackAlloc(frameSize);
        }

        for (int index = 0; index < instructions.size(); index++) {
            final var ins = instructions.get(index);
            switch (ins.getKind()) {
                case MOV -> genMov(ins, index);
                case ADD -> genAdd(ins, index);
                case SUB -> genSub(ins, index);
                case MUL -> genMul(ins, index);
                case RET -> genRet(ins, index);
                default -> throw new RuntimeException("Unknown instruction kind: " + ins.getKind());
            }
            releaseDead(index);
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(path, asmLines);
    }

    private List<Instruction> instructions = List.of();
    private final List<String> asmLines = new ArrayList<>();
    private final Map<IRVariable, Integer> lastUse = new HashMap<>();
    private final Map<IRVariable, Integer> varOffset = new HashMap<>();

    private final Map<IRVariable, String> var2reg = new HashMap<>();
    private final Map<String, IRVariable> reg2var = new HashMap<>();
    private final Set<IRVariable> dirty = new HashSet<>();

    private void collectVariablesAndAssignOffsets() {
        varOffset.clear();
        final var vars = new HashSet<IRVariable>();
        for (final var ins : instructions) {
            if (ins.getKind().isBinary()) {
                vars.add(ins.getResult());
                addIfVar(vars, ins.getLHS());
                addIfVar(vars, ins.getRHS());
            } else if (ins.getKind().isUnary()) {
                vars.add(ins.getResult());
                addIfVar(vars, ins.getFrom());
            } else if (ins.getKind().isReturn()) {
                addIfVar(vars, ins.getReturnValue());
            }
        }
        final var sorted = new ArrayList<>(vars);
        sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < sorted.size(); i++) {
            varOffset.put(sorted.get(i), i * 4);
        }
    }

    private void calcLastUse() {
        lastUse.clear();
        for (int index = 0; index < instructions.size(); index++) {
            final var ins = instructions.get(index);
            if (ins.getKind().isBinary()) {
                recordUse(ins.getLHS(), index);
                recordUse(ins.getRHS(), index);
            } else if (ins.getKind().isUnary()) {
                recordUse(ins.getFrom(), index);
            } else if (ins.getKind().isReturn()) {
                recordUse(ins.getReturnValue(), index);
            }
        }
    }

    private void recordUse(IRValue value, int index) {
        if (value instanceof IRVariable var) {
            lastUse.put(var, index);
        }
    }

    private static void addIfVar(Set<IRVariable> vars, IRValue value) {
        if (value instanceof IRVariable var) {
            vars.add(var);
        }
    }

    private List<Instruction> normalize(List<Instruction> origin) {
        final var result = new ArrayList<Instruction>();
        for (final var ins : origin) {
            if (ins.getKind().isBinary()) {
                final var lhs = ins.getLHS();
                final var rhs = ins.getRHS();
                if (lhs instanceof IRImmediate lImm && rhs instanceof IRImmediate rImm) {
                    final int folded = switch (ins.getKind()) {
                        case ADD -> lImm.getValue() + rImm.getValue();
                        case SUB -> lImm.getValue() - rImm.getValue();
                        case MUL -> lImm.getValue() * rImm.getValue();
                        default -> throw new RuntimeException("Unexpected binary kind");
                    };
                    result.add(Instruction.createMov(ins.getResult(), IRImmediate.of(folded)));
                } else if (ins.getKind() == cn.edu.hitsz.compiler.ir.InstructionKind.ADD) {
                    if (lhs instanceof IRImmediate) {
                        result.add(Instruction.createAdd(ins.getResult(), rhs, lhs));
                    } else {
                        result.add(ins);
                    }
                } else if (ins.getKind() == cn.edu.hitsz.compiler.ir.InstructionKind.SUB) {
                    if (lhs instanceof IRImmediate imm) {
                        final var temp = IRVariable.temp();
                        result.add(Instruction.createMov(temp, imm));
                        result.add(Instruction.createSub(ins.getResult(), temp, rhs));
                    } else if (rhs instanceof IRImmediate) {
                        result.add(ins);
                    } else {
                        result.add(ins);
                    }
                } else if (ins.getKind() == cn.edu.hitsz.compiler.ir.InstructionKind.MUL) {
                    if (lhs instanceof IRImmediate imm) {
                        final var temp = IRVariable.temp();
                        result.add(Instruction.createMov(temp, imm));
                        result.add(Instruction.createMul(ins.getResult(), rhs, temp));
                    } else if (rhs instanceof IRImmediate imm) {
                        final var temp = IRVariable.temp();
                        result.add(Instruction.createMov(temp, imm));
                        result.add(Instruction.createMul(ins.getResult(), lhs, temp));
                    } else {
                        result.add(ins);
                    }
                } else {
                    result.add(ins);
                }
            } else {
                result.add(ins);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void emit(String asm, Instruction ir) {
        asmLines.add("    " + asm + "\t\t#  " + ir);
    }

    private void emitRaw(String asm) {
        asmLines.add("    " + asm);
    }

    private void emitStackAlloc(int frameSize) {
        if (fitsI12(-frameSize)) {
            emitRaw("addi sp, sp, " + (-frameSize));
        } else {
            emitRaw("li t0, " + frameSize);
            emitRaw("sub sp, sp, t0");
        }
    }

    private void genMov(Instruction ins, int index) {
        final var dst = ins.getResult();
        final var from = ins.getFrom();
        if (from instanceof IRImmediate imm) {
            final String rd = ensureWriteReg(dst, index, Set.of());
            emit("li " + rd + ", " + imm.getValue(), ins);
        } else if (from instanceof IRVariable var) {
            final var protectedVars = new HashSet<IRVariable>();
            final String rs = ensureReadReg(var, index, protectedVars);
            protectedVars.add(var);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            emit("mv " + rd + ", " + rs, ins);
        } else {
            throw new RuntimeException("Unknown IRValue in MOV");
        }
        dirty.add(dst);
    }

    private void genAdd(Instruction ins, int index) {
        final var dst = ins.getResult();
        final var lhs = ins.getLHS();
        final var rhs = ins.getRHS();

        final var protectedVars = new HashSet<IRVariable>();
        if (rhs instanceof IRImmediate imm && lhs instanceof IRVariable lv) {
            final String rs = ensureReadReg(lv, index, protectedVars);
            protectedVars.add(lv);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            emit("addi " + rd + ", " + rs + ", " + imm.getValue(), ins);
        } else if (lhs instanceof IRImmediate imm && rhs instanceof IRVariable rv) {
            final String rs = ensureReadReg(rv, index, protectedVars);
            protectedVars.add(rv);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            emit("addi " + rd + ", " + rs + ", " + imm.getValue(), ins);
        } else {
            final var lv = (IRVariable) lhs;
            final var rv = (IRVariable) rhs;
            final String rs1 = ensureReadReg(lv, index, protectedVars);
            protectedVars.add(lv);
            final String rs2 = ensureReadReg(rv, index, protectedVars);
            protectedVars.add(rv);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            emit("add " + rd + ", " + rs1 + ", " + rs2, ins);
        }
        dirty.add(dst);
    }

    private void genSub(Instruction ins, int index) {
        final var dst = ins.getResult();
        final var lhs = ins.getLHS();
        final var rhs = ins.getRHS();

        final var protectedVars = new HashSet<IRVariable>();
        if (rhs instanceof IRImmediate imm && lhs instanceof IRVariable lv) {
            final int neg = -imm.getValue();
            final String rs = ensureReadReg(lv, index, protectedVars);
            protectedVars.add(lv);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            if (fitsI12(neg)) {
                emit("addi " + rd + ", " + rs + ", " + neg, ins);
            } else {
                final String tmp = ensureScratchReg(index, protectedVars);
                emitRaw("li " + tmp + ", " + imm.getValue());
                emit("sub " + rd + ", " + rs + ", " + tmp, ins);
            }
        } else {
            final var lv = (IRVariable) lhs;
            final var rv = (IRVariable) rhs;
            final String rs1 = ensureReadReg(lv, index, protectedVars);
            protectedVars.add(lv);
            final String rs2 = ensureReadReg(rv, index, protectedVars);
            protectedVars.add(rv);
            final String rd = ensureWriteReg(dst, index, protectedVars);
            emit("sub " + rd + ", " + rs1 + ", " + rs2, ins);
        }
        dirty.add(dst);
    }

    private void genMul(Instruction ins, int index) {
        final var dst = ins.getResult();
        final var lhs = ins.getLHS();
        final var rhs = ins.getRHS();

        final var protectedVars = new HashSet<IRVariable>();
        final var lv = (IRVariable) lhs;
        final var rv = (IRVariable) rhs;
        final String rs1 = ensureReadReg(lv, index, protectedVars);
        protectedVars.add(lv);
        final String rs2 = ensureReadReg(rv, index, protectedVars);
        protectedVars.add(rv);
        final String rd = ensureWriteReg(dst, index, protectedVars);
        emit("mul " + rd + ", " + rs1 + ", " + rs2, ins);
        dirty.add(dst);
    }

    private void genRet(Instruction ins, int index) {
        final var value = ins.getReturnValue();
        if (value instanceof IRImmediate imm) {
            emit("li a0, " + imm.getValue(), ins);
        } else if (value instanceof IRVariable var) {
            final String rs = ensureReadReg(var, index, Set.of());
            emit("mv a0, " + rs, ins);
        } else {
            throw new RuntimeException("Unknown IRValue in RET");
        }
    }

    private String ensureReadReg(IRVariable var, int index, Set<IRVariable> protectedVars) {
        if (var2reg.containsKey(var)) {
            return var2reg.get(var);
        }
        final String reg = allocReg(index, protectedVars);
        bind(var, reg, false);
        emitRaw("lw " + reg + ", " + offsetOf(var) + "(sp)");
        return reg;
    }

    private String ensureWriteReg(IRVariable var, int index, Set<IRVariable> protectedVars) {
        if (var2reg.containsKey(var)) {
            return var2reg.get(var);
        }
        final String reg = allocReg(index, protectedVars);
        bind(var, reg, true);
        return reg;
    }

    private String ensureScratchReg(int index, Set<IRVariable> protectedVars) {
        return allocReg(index, protectedVars);
    }

    private void releaseDead(int index) {
        final var vars = new ArrayList<>(var2reg.keySet());
        for (final var var : vars) {
            if (lastUse.getOrDefault(var, -1) == index) {
                unbind(var);
            }
        }
    }

    private String allocReg(int index, Set<IRVariable> protectedVars) {
        for (final var reg : REG_POOL) {
            if (!reg2var.containsKey(reg)) {
                return reg;
            }
        }
        for (final var reg : REG_POOL) {
            final var v = reg2var.get(reg);
            if (v != null && !protectedVars.contains(v) && lastUse.getOrDefault(v, -1) <= index) {
                unbind(v);
                return reg;
            }
        }
        final var victim = chooseVictim(index, protectedVars);
        final var reg = var2reg.get(victim);
        spillIfDirty(victim);
        unbind(victim);
        return reg;
    }

    private IRVariable chooseVictim(int index, Set<IRVariable> protectedVars) {
        IRVariable victim = null;
        int farthest = -1;
        for (final var entry : reg2var.entrySet()) {
            final var var = entry.getValue();
            if (protectedVars.contains(var)) {
                continue;
            }
            final int lu = lastUse.getOrDefault(var, -1);
            if (lu > farthest) {
                farthest = lu;
                victim = var;
            }
        }
        if (victim == null) {
            throw new RuntimeException("No register can be allocated");
        }
        return victim;
    }

    private void spillIfDirty(IRVariable var) {
        if (!dirty.contains(var)) {
            return;
        }
        final var reg = var2reg.get(var);
        if (reg == null) {
            return;
        }
        emitRaw("sw " + reg + ", " + offsetOf(var) + "(sp)");
        dirty.remove(var);
    }

    private void bind(IRVariable var, String reg, boolean markDirty) {
        var2reg.put(var, reg);
        reg2var.put(reg, var);
        if (markDirty) {
            dirty.add(var);
        }
    }

    private void unbind(IRVariable var) {
        final var reg = var2reg.remove(var);
        if (reg != null) {
            reg2var.remove(reg);
        }
        dirty.remove(var);
    }

    private int offsetOf(IRVariable var) {
        final var offset = varOffset.get(var);
        if (offset == null) {
            throw new RuntimeException("Unknown variable: " + var.getName());
        }
        return offset;
    }

    private static int alignTo(int n, int align) {
        final int r = n % align;
        return r == 0 ? n : n + (align - r);
    }

    private static boolean fitsI12(int imm) {
        return imm >= -2048 && imm <= 2047;
    }
}
