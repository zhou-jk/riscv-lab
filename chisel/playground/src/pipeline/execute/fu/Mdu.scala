package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Multiplier extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  // --- 提取输入和准备操作数 ---
  val op     = io.info.op(3, 0) // 提取 MDU 操作码 (假设低 4 位)
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data

  // 65位扩展
  val src1_sext = Wire(SInt((XLEN + 1).W))
  val src2_sext = Wire(SInt((XLEN + 1).W))
  val src1_zext = Wire(UInt((XLEN + 1).W))
  val src2_zext = Wire(UInt((XLEN + 1).W))

  src1_sext := Cat(src1(XLEN - 1), src1).asSInt
  src2_sext := Cat(src2(XLEN - 1), src2).asSInt
  src1_zext := Cat(0.U(1.W), src1)
  src2_zext := Cat(0.U(1.W), src2)

  // --- 乘法运算 ---
  // 65位乘法结果是 130 位
  val mul_ss_130 = (src1_sext * src2_sext).asUInt
  val mul_su_130 = (src1_sext * src2_zext.asSInt).asUInt // SInt * SInt (无符号转SInt)
  val mul_uu_130 = (src1_zext * src2_zext)

  // 截取高低 64 位 (从 130 位结果中)
  val mul_ss_h = mul_ss_130(XLEN * 2 - 1, XLEN)
  val mul_su_h = mul_su_130(XLEN * 2 - 1, XLEN)
  val mul_uu_h = mul_uu_130(XLEN * 2 - 1, XLEN)
  val mul_l    = mul_ss_130(XLEN - 1, 0) // 低位与符号无关

  // --- 32 位乘法 (MULW) ---
  val src1_32 = src1(31, 0)
  val src2_32 = src2(31, 0)

  // 33位扩展
  val src1_33_sext = Wire(SInt(33.W))
  val src2_33_sext = Wire(SInt(33.W))
  src1_33_sext := Cat(src1_32(31), src1_32).asSInt
  src2_33_sext := Cat(src2_32(31), src2_32).asSInt

  // 33位乘法结果 66 位
  val mul_w_66 = (src1_33_sext * src2_33_sext).asUInt

  // 取低 32 位并符号扩展到 64 位
  val mulw_result = Cat(Fill(XLEN - 32, mul_w_66(31)), mul_w_66(31, 0))

  // --- 选择结果 ---
  val mul_result = MuxLookup(op, mul_l)(Seq( // 默认 mul
    MDUOpType.mul    -> mul_l,
    MDUOpType.mulh   -> mul_ss_h,
    MDUOpType.mulhsu -> mul_su_h,
    MDUOpType.mulhu  -> mul_uu_h,
    MDUOpType.mulw   -> mulw_result
  ))

  io.result := mul_result
}

class Divider extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info()) // 假设 Info 包含 pc, valid, fusel
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  // --- 提取输入 ---
  val op     = io.info.op(3, 0) // 提取 MDU 操作码
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data

  // --- 准备操作数 ---
  val src1_s = src1.asSInt
  val src2_s = src2.asSInt
  val src1_u = src1
  val src2_u = src2

  val src1_32 = src1(31, 0)
  val src2_32 = src2(31, 0)
  val src1_32_s = src1_32.asSInt
  val src2_32_s = src2_32.asSInt
  val src1_32_u = src1_32
  val src2_32_u = src2_32

  // --- 定义常量 ---
  // 使用 BigInt 定义 MIN_SINT 可能更健壮
  val MIN_SINT64 = (BigInt(1) << (XLEN - 1)).S(XLEN.W)
  val MAX_UINT64 = Fill(XLEN, 1.B) // 全 1 UInt
  val NEG_ONE64 = (-1).S(XLEN.W)

  val MIN_SINT32 = (BigInt(1) << 31).S(32.W)
  val MAX_UINT32 = Fill(32, 1.B) // 全 1 UInt
  val NEG_ONE32 = (-1).S(32.W)

  // --- 特殊情况判断 ---
  val divisor_is_zero64 = (src2_u === 0.U)
  val divisor_is_zero32 = (src2_32_u === 0.U)
  val overflow64 = (src1_s === MIN_SINT64) && (src2_s === NEG_ONE64)
  val overflow32 = (src1_32_s === MIN_SINT32) && (src2_32_s === NEG_ONE32)

  // --- 计算原始 SInt 余数 (用于 remw 调试和符号判断) ---
  val remainder_sint = Wire(SInt(32.W))
  when (divisor_is_zero32) {
    remainder_sint := src1_32_s // 返回被除数
  } .elsewhen (overflow32) {
    remainder_sint := 0.S(32.W) // 返回 0
  } .otherwise {
    remainder_sint := src1_32_s % src2_32_s // 正常计算
  }
  val rem32 = remainder_sint.asUInt // 获取 UInt 位模式

  // --- 计算除法/取余结果 (处理特殊情况) ---
  // 64位
  val divu64  = Mux(divisor_is_zero64, MAX_UINT64, src1_u / src2_u)
  val remu64  = Mux(divisor_is_zero64, src1_u, src1_u % src2_u)
  val div64   = Mux(divisor_is_zero64, MAX_UINT64, // 除零返回 -1 (全1)
                  Mux(overflow64, MIN_SINT64.asUInt, (src1_s / src2_s).asUInt))
  val rem64   = Mux(divisor_is_zero64, src1_u, // 除零返回被除数
                  Mux(overflow64, 0.U(XLEN.W), (src1_s % src2_s).asUInt))

  // 32位
  val divu32  = Mux(divisor_is_zero32, MAX_UINT32, src1_32_u / src2_32_u)
  val remu32_calc = Mux(divisor_is_zero32, src1_32_u, src1_32_u % src2_32_u) // 实际 remu32 计算
  val div32   = Mux(divisor_is_zero32, MAX_UINT32, // 除零返回 -1 (全1)
                  Mux(overflow32, MIN_SINT32.asUInt, (src1_32_s / src2_32_s).asUInt))
  // val rem32 在上面已经计算过 (remainder_sint.asUInt)

  // --- 字操作结果扩展 ---
  val divw_result  = Cat(Fill(XLEN - 32, div32(31)), div32)     // 符号扩展
  val remw_result  = Cat(Fill(XLEN - 32, rem32(31)), rem32)     // 符号扩展 (使用 rem32 的最高位)
  val divuw_result = Cat(Fill(XLEN - 32, 0.U), divu32)         // *** 零扩展 ***
  val remuw_result = Cat(Fill(XLEN - 32, 0.U), remu32_calc)    // *** 零扩展 *** (使用 remu32_calc)


  // --- 选择结果 ---
  val div_rem_result = MuxLookup(op, 0.U(XLEN.W))(Seq(
    MDUOpType.div   -> div64,
    MDUOpType.divu  -> divu64,
    MDUOpType.rem   -> rem64,
    MDUOpType.remu  -> remu64,
    MDUOpType.divw  -> divw_result,
    MDUOpType.divuw -> divuw_result, // 使用修正后的零扩展结果
    MDUOpType.remw  -> remw_result,
    MDUOpType.remuw -> remuw_result  // 使用修正后的零扩展结果
  ))

  io.result := div_rem_result

}

class Mdu extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  // 1. 实例化子模块
  val multiplier = Module(new Multiplier()).io
  val divider    = Module(new Divider()).io

  // 2. 连接输入
  multiplier.info     := io.info
  multiplier.src_info := io.src_info
  divider.info        := io.info
  divider.src_info    := io.src_info

  // 3. 根据操作类型选择结果
  val mdu_op = io.info.op(3, 0) // 提取 MDU 操作码
  // 假设 MDUOpType.isMul 检查 op(2) === 0.B
  val is_mul_operation = MDUOpType.isMul(mdu_op)

  io.result := Mux(is_mul_operation, multiplier.result, divider.result)
}