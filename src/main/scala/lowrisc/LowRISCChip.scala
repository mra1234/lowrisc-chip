// See LICENSE.Cambridge for license details.

package freechips.rocketchip.lowrisc

import Chisel._
import chisel3.core.{Input, Output, attach, Param, IntParam}
import chisel3.experimental.{RawModule, withClockAndReset}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.ElaborationArtefacts

// the Application core complex for lowRISC SoC
class LoRCCoreplex(implicit p: Parameters) extends RocketCoreplex
    with HasMasterAXI4MemPort
    with HasAXI4VirtualBus
    with HasSlaveAXI4Port
    with HasPeripheryBootROM
    with HasSystemErrorSlave {
  override lazy val module = new LoRCCoreplexModule(this)
}

class LoRCCoreplexModule[+L <: LoRCCoreplex](_outer: L) extends RocketCoreplexModule(_outer)
    with HasRTCModuleImp
    with HasMasterAXI4MemPortModuleImp
    with HasAXI4VirtualBusModuleImp
    with HasSlaveAXI4PortModuleImp
    with HasPeripheryBootROMModuleImp

class CoreplexTop()(implicit p: Parameters) extends RawModule {

  val coreplex = LazyModule(new LoRCCoreplex)
  val clk = IO(Input(Clock()))
  val rst = IO(Input(Bool()))
  val TCK = IO(Input(Clock()))
  val TMS = IO(Input(Bool()))
  val TDI = IO(Input(Bool()))
  val TDO = IO(Output(Bool()))

  val interrupts = IO(UInt(INPUT, width = coreplex.int_size))
  val mem = IO(coreplex.mem_axi4.bundleOut.cloneType)
  val mmio_master = IO(coreplex.mmio_axi4.bundleOut.cloneType)
  val mmio_slave = IO(Flipped(coreplex.l2FrontendAXI4Node.bundleIn.cloneType))
  val success = IO(Output(Bool()))

  withClockAndReset(clk, rst) {
    val rocket = Module(coreplex.module)
    rocket.mmio_interrupts <> interrupts
    mem <> rocket.mem_axi4
    mmio_master <> rocket.mmio_axi4
    rocket.l2_frontend_bus_axi4 <> mmio_slave

    rocket.debug.systemjtag.foreach { sj =>
      sj.jtag.TCK <> TCK
      sj.jtag.TMS <> TMS
      sj.jtag.TDI <> TDI
      TDO <> sj.jtag.TDO.data
      val rst = sj.reset
      
      sj.mfr_id := UInt(42) // dummy value
    }

//    jtagout <> rocket.debug.systemjtag
  }


  DumpMacro("MEM_ID_WIDTH",   mem(0).ar.bits.id.getWidth)
  DumpMacro("MEM_ADDR_WIDTH", mem(0).ar.bits.addr.getWidth)
  DumpMacro("MEM_DATA_WIDTH", mem(0).w.bits.data.getWidth)
  if(!mem(0).ar.bits.user.isEmpty)
    DumpMacro("MEM_USER_WIDTH", mem(0).ar.bits.user.get.getWidth)

  DumpMacro("MMIO_MASTER_ID_WIDTH",   mmio_master(0).ar.bits.id.getWidth)
  DumpMacro("MMIO_MASTER_ADDR_WIDTH", mmio_master(0).ar.bits.addr.getWidth)
  DumpMacro("MMIO_MASTER_DATA_WIDTH", mmio_master(0).w.bits.data.getWidth)
  if(!mmio_master(0).ar.bits.user.isEmpty)
    DumpMacro("MMIO_MASTER_USER_WIDTH", mmio_master(0).ar.bits.user.get.getWidth)

  DumpMacro("MMIO_SLAVE_ID_WIDTH",   mmio_slave(0).ar.bits.id.getWidth)
  DumpMacro("MMIO_SLAVE_ADDR_WIDTH", mmio_slave(0).ar.bits.addr.getWidth)
  DumpMacro("MMIO_SLAVE_DATA_WIDTH", mmio_slave(0).w.bits.data.getWidth)
  if(!mmio_slave(0).ar.bits.user.isEmpty)
    DumpMacro("MMIO_SLAVE_USER_WIDTH", mmio_slave(0).ar.bits.user.get.getWidth)

  ElaborationArtefacts.add("vh", DumpMacro.genVH("LoRCCoreplex_HD"))
  ElaborationArtefacts.add("hpp", DumpMacro.genHPP("LoRCCoreplex_HD"))

}
