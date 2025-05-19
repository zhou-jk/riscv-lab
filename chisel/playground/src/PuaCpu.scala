import chisel3._
import chisel3.util._
import cpu._
import cpu.defines._

class PuaCpu extends Module {
  val io = IO(new Bundle {
    val ext_int   = Input(new ExtInterrupt())
    val axi       = new AXIMaster()
    val debug     = new DEBUG()
  })

  val core = Module(new Core())

  io.ext_int <> core.io.interrupt
  io.axi <> core.io.axi
  io.debug <> core.io.debug
}
