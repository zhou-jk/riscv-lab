package cpu

import chisel3._
import chisel3.util._

import defines._
import defines.Const._
import pipeline._

class Core extends Module {
  val io = IO(new Bundle {
    val interrupt = Input(new ExtInterrupt())
    val instSram  = new InstSram()
    val dataSram  = new DataSram()
    val debug     = new DEBUG()
  })

  val fetchUnit      = Module(new FetchUnit()).io
  val decodeStage    = Module(new DecodeStage()).io
  val decodeUnit     = Module(new DecodeUnit()).io
  val regfile        = Module(new ARegFile()).io
  val executeStage   = Module(new ExecuteStage()).io
  val executeUnit    = Module(new ExecuteUnit()).io
  val memoryStage    = Module(new MemoryStage()).io
  val memoryUnit     = Module(new MemoryUnit()).io
  val writeBackStage = Module(new WriteBackStage()).io
  val writeBackUnit  = Module(new WriteBackUnit()).io
  val controlUnit    = Module(new ControlUnit()).io

  // 控制单元
  controlUnit.decodeUnitInfo    := decodeUnit.executeStage.data.info
  controlUnit.executeUnitInfo   := executeUnit.memoryStage.data.info
  controlUnit.memoryUnitInfo    := memoryUnit.writeBackStage.data.info
  controlUnit.writeBackUnitInfo := writeBackUnit.writeBackStage.data.info
  controlUnit.flush            := executeUnit.flush

  // 取指单元
  fetchUnit.instSram <> io.instSram
  fetchUnit.decodeStage <> decodeStage.fetchUnit
  fetchUnit.flush := executeUnit.flush
  fetchUnit.target := executeUnit.target
  fetchUnit.ctrl   := controlUnit.fetchUnitCtrl

  decodeStage.decodeUnit <> decodeUnit.decodeStage
  decodeStage.ctrl := controlUnit.fetchUnitCtrl

  regfile.read.src1.raddr := decodeUnit.regfile.src1.raddr
  regfile.read.src2.raddr := decodeUnit.regfile.src2.raddr

  decodeUnit.regfile.src1.rdata := regfile.read.src1.rdata
  decodeUnit.regfile.src2.rdata := regfile.read.src2.rdata
  
  // 连接CSR相关信号
  decodeUnit.mode := executeUnit.mode
  decodeUnit.interrupt := executeUnit.interrupt_out

  // 连接前递数据
  val exeForwardInfo = Wire(new ForwardInfo())
  exeForwardInfo.reg_wen := executeUnit.memoryStage.data.info.reg_wen
  exeForwardInfo.reg_waddr := executeUnit.memoryStage.data.info.reg_waddr
  exeForwardInfo.fusel := executeUnit.memoryStage.data.info.fusel

  val exeForwardData = Wire(new ForwardData())
  exeForwardData.wdata := executeUnit.memoryStage.data.rd_info.wdata

  val memForwardInfo = Wire(new ForwardInfo())
  memForwardInfo.reg_wen := memoryUnit.writeBackStage.data.info.reg_wen
  memForwardInfo.reg_waddr := memoryUnit.writeBackStage.data.info.reg_waddr
  memForwardInfo.fusel := memoryUnit.writeBackStage.data.info.fusel

  val memForwardData = Wire(new ForwardData())
  memForwardData.wdata := memoryUnit.writeBackStage.data.rd_info.wdata

  val wbForwardInfo = Wire(new ForwardInfo())
  wbForwardInfo.reg_wen := writeBackUnit.writeBackStage.data.info.reg_wen
  wbForwardInfo.reg_waddr := writeBackUnit.writeBackStage.data.info.reg_waddr
  wbForwardInfo.fusel := writeBackUnit.writeBackStage.data.info.fusel

  val wbForwardData = Wire(new ForwardData())
  wbForwardData.wdata := writeBackUnit.writeBackStage.data.rd_info.wdata

  decodeUnit.exeForwardInfo := exeForwardInfo
  decodeUnit.memForwardInfo := memForwardInfo
  decodeUnit.wbForwardInfo  := wbForwardInfo

  decodeUnit.exeForwardData := exeForwardData
  decodeUnit.memForwardData := memForwardData
  decodeUnit.wbForwardData  := wbForwardData

  decodeUnit.executeStage <> executeStage.decodeUnit

  // 执行阶段
  executeStage.executeUnit <> executeUnit.executeStage
  executeStage.ctrl := controlUnit.decodeUnitCtrl
  executeUnit.memoryStage  <> memoryStage.executeUnit
  
  // 连接中断信号
  executeUnit.interrupt := io.interrupt

  // 访存阶段
  memoryStage.memoryUnit <> memoryUnit.memoryStage
  memoryStage.ctrl := controlUnit.executeUnitCtrl

  io.dataSram.rdata <> memoryUnit.rdata
  memoryUnit.writeBackStage <> writeBackStage.memoryUnit

  executeUnit.dataSram <> io.dataSram

  // 写回阶段
  writeBackStage.writeBackUnit <> writeBackUnit.writeBackStage
  writeBackStage.ctrl := controlUnit.writeBackUnitCtrl

  regfile.write.wen   := writeBackUnit.regfile.wen
  regfile.write.waddr := writeBackUnit.regfile.waddr
  regfile.write.wdata := writeBackUnit.regfile.wdata

  io.debug <> writeBackUnit.debug
}