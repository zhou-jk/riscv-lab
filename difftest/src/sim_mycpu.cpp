#include "verilated.h"
// #include "verilated_fst_c.h"
#include "verilated_fst_c.h"
#include "Vtop.h"
#include "rv_systembus.hpp"
#include "rv_core.hpp"
#include "rv_clint.hpp"
#include "rv_plic.hpp"
#include <stdio.h>

bool running = true;
bool run_riscv_test = false;
bool dump_pc_history = false;
bool print_pc = false;
bool should_delay = false;
bool dual_issue = false;
bool perf_counter = false;
bool init_gprs = false;
bool write_append = false;
const uint64_t commit_timeout = 3000;
const uint64_t print_pc_cycle = 5e5;
long trace_start_time = 0; // -starttrace [time]
std::atomic_bool trace_on = false;
long sim_time = 1e8;

long long total_cycle = 0;
long long total_instr = 0;

VerilatedFstC fst;

void open_trace()
{
    fst.open("trace.fst");
    trace_on.store(true, std::memory_order_seq_cst);
}

#undef assert
void assert(bool expr, const char *msg = "")
{
    if (!expr)
    {
        running = false;
        printf("%s\n", msg);
        printf("soc_simulator assert failed!\n");
    }
}

#include "axi4.hpp"
#include "axi4_mem.hpp"
#include "axi4_xbar.hpp"
#include "mmio_mem.hpp"

#include <iostream>
#include <termios.h>
#include <unistd.h>
#include <thread>
#include <csignal>
#include <sstream>

void connect_wire(axi4_ptr<32, 64, 4> &mmio_ptr, Vtop *top)
{
    // connect
    // mmio
    // aw
    mmio_ptr.awaddr = &(top->axi_awaddr);
    mmio_ptr.awburst = &(top->axi_awburst);
    mmio_ptr.awid = &(top->axi_awid);
    mmio_ptr.awlen = &(top->axi_awlen);
    mmio_ptr.awready = &(top->axi_awready);
    mmio_ptr.awsize = &(top->axi_awsize);
    mmio_ptr.awvalid = &(top->axi_awvalid);
    // w
    mmio_ptr.wdata = &(top->axi_wdata);
    mmio_ptr.wlast = &(top->axi_wlast);
    mmio_ptr.wready = &(top->axi_wready);
    mmio_ptr.wstrb = &(top->axi_wstrb);
    mmio_ptr.wvalid = &(top->axi_wvalid);
    // b
    mmio_ptr.bid = &(top->axi_bid);
    mmio_ptr.bready = &(top->axi_bready);
    mmio_ptr.bresp = &(top->axi_bresp);
    mmio_ptr.bvalid = &(top->axi_bvalid);
    // ar
    mmio_ptr.araddr = &(top->axi_araddr);
    mmio_ptr.arburst = &(top->axi_arburst);
    mmio_ptr.arid = &(top->axi_arid);
    mmio_ptr.arlen = &(top->axi_arlen);
    mmio_ptr.arready = &(top->axi_arready);
    mmio_ptr.arsize = &(top->axi_arsize);
    mmio_ptr.arvalid = &(top->axi_arvalid);
    // r
    mmio_ptr.rdata = &(top->axi_rdata);
    mmio_ptr.rid = &(top->axi_rid);
    mmio_ptr.rlast = &(top->axi_rlast);
    mmio_ptr.rready = &(top->axi_rready);
    mmio_ptr.rresp = &(top->axi_rresp);
    mmio_ptr.rvalid = &(top->axi_rvalid);
}

void riscv_test_run(Vtop *top, axi4_ref<32, 64, 4> &mmio_ref, const char *riscv_test_path)
{

    // setup cemu {
    rv_systembus cemu_system_bus;
    mmio_mem cemu_mem(128 * 1024 * 1024, riscv_test_path);

    assert(cemu_system_bus.add_dev(0x80000000, 0x80000000, &cemu_mem));

    rv_core cemu_rvcore(cemu_system_bus);
    cemu_rvcore.jump(0x80000000);
    if (init_gprs)
    {
        for (int i = 0; i < 32; i++)
        {
            cemu_rvcore.set_GPR(i, i);
        }
    }
    // cemu_rvcore.set_difftest_mode(true);
    // setup cemu }

    // setup rtl {
    axi4<32, 64, 4> mmio_sigs;
    axi4_ref<32, 64, 4> mmio_sigs_ref(mmio_sigs);
    axi4_xbar<32, 64, 4> mmio;

    mmio_mem rtl_mem(128 * 1024 * 1024, riscv_test_path);
    assert(mmio.add_dev(0x80000000, 0x80000000, &rtl_mem));
    // setup rtl }

    // connect Vcd for trace
    if (trace_on)
    {
        top->trace(&fst, 0);
        fst.open("trace.fst");
    }
    uint64_t rst_ticks = 10;
    uint64_t ticks = 0;
    uint64_t last_commit = ticks;
    uint64_t pc_cnt = print_pc_cycle;
    int delay = 1500;
    while (!Verilated::gotFinish() && sim_time > 0 && running)
    {
        if (rst_ticks > 0)
        {
            top->reset = 1;
            rst_ticks--;
        }
        else
            top->reset = 0;
        top->clock = !top->clock;
        if (top->clock && !top->reset)
            mmio_sigs.update_input(mmio_ref);
        top->eval();
        if (top->clock && !top->reset)
        {
            mmio.beat(mmio_sigs_ref);
            mmio_sigs.update_output(mmio_ref);
            top->eval();
        }
        //===性能计数器=====
        if (!top->reset && top->clock)
            total_cycle++;
        if (((top->clock && !dual_issue) || dual_issue) && top->debug_commit)
            total_instr++;
        //==================
        if (((top->clock && !dual_issue) || dual_issue) && top->debug_commit)
        { // instr retire
            // cemu_rvcore.import_diff_test_info(top->debug_csr_mcycle, top->debug_csr_minstret, top->debug_csr_mip, top->debug_csr_interrupt);
            cemu_rvcore.step(0, 0, 0, 0);
            last_commit = ticks;
            if (pc_cnt++ >= print_pc_cycle && print_pc)
            {
                printf("PC = 0x%016lx\n", cemu_rvcore.debug_pc);
                pc_cnt = 0;
            }
            if ((top->debug_pc != cemu_rvcore.debug_pc ||
                 cemu_rvcore.debug_reg_num != 0 &&
                     (top->debug_rf_wnum != cemu_rvcore.debug_reg_num ||
                      top->debug_rf_wdata != cemu_rvcore.debug_reg_wdata && !(cemu_rvcore.debug_is_mcycle || cemu_rvcore.debug_is_minstret))))
            {
                printf("\033[1;31mError!\033[0m\n");
                printf("reference: PC = 0x%016lx, wb_rf_wnum = 0x%02lx, wb_rf_wdata = 0x%016lx\n", cemu_rvcore.debug_pc, cemu_rvcore.debug_reg_num, cemu_rvcore.debug_reg_wdata);
                printf("mycpu    : PC = 0x%016lx, wb_rf_wnum = 0x%02x, wb_rf_wdata = 0x%016lx\n", top->debug_pc, top->debug_rf_wnum, top->debug_rf_wdata);
                if (!should_delay)
                {
                    running = false;
                    if (dump_pc_history)
                        cemu_rvcore.dump_pc_history();
                }
                else if (dump_pc_history && delay-- == 10)
                    cemu_rvcore.dump_pc_history();
                else if (delay-- == 0)
                    running = false;
            }
            else
            {
                if (cemu_rvcore.debug_is_mcycle || cemu_rvcore.debug_is_minstret)
                {
                    cemu_rvcore.set_GPR(cemu_rvcore.debug_reg_num, top->debug_rf_wdata);
                }
            }
        }
        if (trace_on)
        {
            fst.dump(ticks);
            sim_time--;
        }
        ticks++;
        if (ticks - last_commit >= commit_timeout)
        {
            printf("\033[1;31mError!\033[0m\n");
            printf("CPU stuck for %ld cycles!\n", commit_timeout / 2);
            running = false;
            if (dump_pc_history)
                cemu_rvcore.dump_pc_history();
        }
    }

    printf("total_ticks: %lu\n", ticks);
}

void make_cpu_trace(Vtop *top, axi4_ref<32, 64, 4> &mmio_ref, const char *riscv_test_path)
{

    // setup cemu {
    rv_systembus cemu_system_bus;
    mmio_mem cemu_mem(128 * 1024 * 1024, riscv_test_path);

    assert(cemu_system_bus.add_dev(0x80000000, 0x80000000, &cemu_mem));

    rv_core cemu_rvcore(cemu_system_bus);
    cemu_rvcore.jump(0x80000000);
    if (init_gprs)
    {
        for (int i = 0; i < 32; i++)
        {
            cemu_rvcore.set_GPR(i, i);
        }
    }
    // setup cemu }

    // setup rtl {
    axi4<32, 64, 4> mmio_sigs;
    axi4_ref<32, 64, 4> mmio_sigs_ref(mmio_sigs);
    axi4_xbar<32, 64, 4> mmio;

    mmio_mem rtl_mem(128 * 1024 * 1024, riscv_test_path);

    assert(mmio.add_dev(0x80000000, 0x80000000, &rtl_mem));
    // setup rtl }

    // connect Vcd for trace
    if (trace_on)
    {
        top->trace(&fst, 0);
        fst.open("trace.fst");
    }

    FILE *trace_file;
    if (write_append)
    {
        trace_file = fopen("trace.txt", "a");
    }
    else
    {
        trace_file = fopen("trace.txt", "w");
    }
    if (trace_file == NULL)
    {
        printf("Error opening file!\n");
    }

    uint64_t rst_ticks = 10;
    uint64_t ticks = 0;
    uint64_t last_commit = ticks;
    int delay = 10;
    while (!Verilated::gotFinish() && sim_time > 0 && running)
    {
        if (rst_ticks > 0)
        {
            top->reset = 1;
            rst_ticks--;
        }
        else
            top->reset = 0;
        top->clock = !top->clock;
        if (top->clock && !top->reset)
            mmio_sigs.update_input(mmio_ref);
        top->eval();
        if (top->clock && !top->reset)
        {
            mmio.beat(mmio_sigs_ref);
            mmio_sigs.update_output(mmio_ref);
            top->eval();
        }
        if (((top->clock && !dual_issue) || dual_issue) && top->debug_commit)
        { // instr retire
            cemu_rvcore.step(0, 0, 0, 0);
            last_commit = ticks;
            if ((top->debug_pc != cemu_rvcore.debug_pc ||
                 cemu_rvcore.debug_reg_num != 0 &&
                     (top->debug_rf_wnum != cemu_rvcore.debug_reg_num ||
                      top->debug_rf_wdata != cemu_rvcore.debug_reg_wdata)) &&
                running)
            {
                printf("\033[1;31mError!\033[0m\n");
                printf("reference: PC = 0x%016lx, wb_rf_wnum = 0x%02lx, wb_rf_wdata = 0x%016lx\n", cemu_rvcore.debug_pc, cemu_rvcore.debug_reg_num, cemu_rvcore.debug_reg_wdata);
                printf("mycpu    : PC = 0x%016lx, wb_rf_wnum = 0x%02x, wb_rf_wdata = 0x%016lx\n", top->debug_pc, top->debug_rf_wnum, top->debug_rf_wdata);
                if (!should_delay)
                {
                    running = false;
                    if (dump_pc_history)
                        cemu_rvcore.dump_pc_history();
                }
                else if (dump_pc_history && delay-- == 10)
                    cemu_rvcore.dump_pc_history();
                else if (delay-- == 0)
                    running = false;
            }
            fprintf(trace_file, "1 %016lx %02lx %016lx\n", cemu_rvcore.debug_pc, cemu_rvcore.debug_reg_num, cemu_rvcore.debug_reg_wdata);
        }
        if (trace_on)
        {
            fst.dump(ticks);
            sim_time--;
        }
        ticks++;
        if (ticks - last_commit >= commit_timeout)
        {
            printf("\033[1;31mError!\033[0m\n");
            printf("CPU stuck for %ld cycles!\n", commit_timeout / 2);
            running = false;
            if (dump_pc_history)
                cemu_rvcore.dump_pc_history();
        }
    }
    printf("total_ticks: %lu\n", ticks);
    fclose(trace_file);
}

int main(int argc, char **argv, char **env)
{
    Verilated::commandArgs(argc, argv);

    std::signal(SIGINT, [](int)
                { running = false; });

    char *file_load_path;
    enum
    {
        NOP,
        RISCV_TEST,
        CPU_TRACE,
    } run_mode = RISCV_TEST;

    for (int i = 1; i < argc; i++)
    {
        if (strcmp(argv[i], "-trace") == 0)
        {
            trace_on = true;
            if (i + 1 < argc)
            {
                sscanf(argv[++i], "%lu", &sim_time);
            }
        }
        else if (strcmp(argv[i], "-starttrace") == 0)
        {
            if (i + 1 < argc)
            {
                sscanf(argv[++i], "%lu", &trace_start_time);
            }
            printf("trace start time: %lu\n", trace_start_time);
        }
        else if (strcmp(argv[i], "-rvtest") == 0)
        {
            run_riscv_test = true;
            run_mode = RISCV_TEST;
        }
        else if (strcmp(argv[i], "-perf") == 0)
        {
            perf_counter = true;
        }
        else if (strcmp(argv[i], "-pc") == 0) // 打印历史PC
        {
            dump_pc_history = true;
        }
        else if (strcmp(argv[i], "-printpc") == 0) // 间隔一定时间输出一次PC
        {
            print_pc = true;
        }
        else if (strcmp(argv[i], "-delay") == 0) // 出错后延迟一段时间再停止
        {
            should_delay = true;
        }
        else if (strcmp(argv[i], "-cpu_trace") == 0) // 生成cpu trace
        {
            run_mode = CPU_TRACE;
        }
        else if (strcmp(argv[i], "-initgprs") == 0) // 初始化寄存器
        {
            init_gprs = true;
        }
        else if (strcmp(argv[i], "-writeappend") == 0) // 追加写入
        {
            write_append = true;
        }
        else if (strcmp(argv[i], "-dual_issue") == 0) // 追加写入
        {
            dual_issue = true;
        }
        else
        {
            file_load_path = argv[i];
        }
    }

    Verilated::traceEverOn(true);

    // setup soc
    Vtop *top = new Vtop;
    axi4_ptr<32, 64, 4> mmio_ptr;

    connect_wire(mmio_ptr, top);
    assert(mmio_ptr.check());

    axi4_ref<32, 64, 4> mmio_ref(mmio_ptr);

    switch (run_mode)
    {
    case RISCV_TEST:
        riscv_test_run(top, mmio_ref, file_load_path);
        break;
    case CPU_TRACE:
        make_cpu_trace(top, mmio_ref, file_load_path);
        break;
    default:
        printf("Unknown running mode.\n");
        exit(-ENOENT);
    }

    return 0;
}
