TOP_NAME := top
SRC_DIR  := ./core
SRC_FILE := $(shell find $(SRC_DIR) -name '*.svh') $(shell find $(SRC_DIR) -name '*.h') $(shell find $(SRC_DIR) -name '*.v') $(shell find $(SRC_DIR) -name '*.sv')
CHISEL_DIR = ../chisel
BUILD_DIR = $(CHISEL_DIR)/build
TESTBIN_DIR = ./test/bin/am-tests/add.bin

.PHONY: clean

obj_dir/V$(TOP_NAME): src/* $(SRC_FILE)
	verilator --cc -Wno-fatal --exe --trace-fst --trace-structs -LDFLAGS "-lpthread" --build src/sim_mycpu.cpp $(SRC_FILE) -I$(SRC_DIR) --top $(TOP_NAME) -j `nproc`

verilog:
	$(MAKE) -C $(CHISEL_DIR) verilog
	cp $(CHISEL_DIR)/build/PuaCpu.v $(SRC_DIR)

trace: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) $(TESTBIN_DIR) -rvtest -trace 10000000 -pc

test:
	$(MAKE) -C $(CHISEL_DIR) test

clean:
	rm -rf obj_dir
	rm -rf core/Puacpu.v

perf: obj_dir/V$(TOP_NAME)
	$(call git_commit, "perf test RTL") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in $$(ls ./test/bin/riscv-test/benchmarks/* | sort); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc -perf; \
	done; \

TEST67 := lab6 lab7
TRACE_TESTS67 := $(addprefix trace_,$(TEST67))

$(TEST67): obj_dir/V$(TOP_NAME)
	$(call git_commit, "test $@") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in $$(ls ./test/bin/am-tests/* | sort); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc; \
	done; \
	count=$$((count + 1)); \
	echo "Running test $$count: ./test/bin/lab-test/lab6.bin"; \
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab6.bin -rvtest -pc -perf; \
	echo "Total tests run: $$count";

$(TRACE_TESTS67): obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace $*") # DO NOT REMOVE THIS LINE!!!
	rm -rf ./trace.txt
	count=0; \
	for test in $$(ls ./test/bin/am-tests/* | sort); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -cpu_trace -writeappend; \
	done; \
	count=$$((count + 1)); \
	echo "Running test $$count: ./test/bin/lab-test/lab6.bin"; \
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab6.bin -rvtest -cpu_trace -writeappend; \
	echo "Total tests run: $$count";

qbench: obj_dir/V$(TOP_NAME)
	@echo "==================================================================="
	@echo "=========================quick benchmarks=========================="
	@echo "==================================================================="
	@count=0; \
	for test in $$(find ./test/bin/riscv-test/benchmarks \( -name "*.bin" \) | sort | grep -vE "*dhrystone*|*mt-vvadd*"); do \
		count=$$((count + 1)); \
		echo "Running bench $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc -perf; \
	done; \

sbench: obj_dir/V$(TOP_NAME)
	@echo "====================================================================="
	@echo "====================quick superscalar benchmarks====================="
	@echo "====================================================================="
	@count=0; \
	for test in $$(find ./test/bin/riscv-test/benchmarks \( -name "*.bin" \) | sort | grep -vE "*dhrystone*|*mt-vvadd*"); do \
		count=$$((count + 1)); \
		echo "Running bench $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc -perf -dual_issue; \
	done; \

TEST9_10 := lab9 lab10
TRACE_TEST9_10 := $(addprefix trace_,$(TEST9_10))

$(TEST9_10): obj_dir/V$(TOP_NAME)
	$(call git_commit, "test $@") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64ui-p-fence_i|*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc; \
	done; \
	echo "Total tests run: $$count";
	$(MAKE) qbench

$(TRACE_TEST9_10): obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace $*") # DO NOT REMOVE THIS LINE!!!
	rm -rf ./trace.txt
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64ui-p-fence_i|*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -cpu_trace -writeappend; \
	done; \
	echo "Total tests run: $$count";

TEST11_13 := lab11 lab12 lab13
TRACE_TEST11_13 := $(addprefix trace_,$(TEST11_13))

$(TEST11_13): obj_dir/V$(TOP_NAME)
	$(call git_commit, "test $@") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		if [ "$@" = "lab13" ]; then \
            ./obj_dir/V$(TOP_NAME) $$test -rvtest -pc -dual_issue; \
        else \
            ./obj_dir/V$(TOP_NAME) $$test -rvtest -pc; \
        fi; \
	done; \
	echo "Total tests run: $$count"; \
	if [ "$@" = "lab13" ]; then \
		$(MAKE) sbench; \
	else \
		$(MAKE) qbench; \
	fi;

$(TRACE_TEST11_13): obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace $*") # DO NOT REMOVE THIS LINE!!!
	rm -rf ./trace.txt
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		if [ "$@" = "trace_lab13" ]; then \
            ./obj_dir/V$(TOP_NAME) $$test -rvtest -cpu_trace -writeappend -dual_issue; \
        else \
            ./obj_dir/V$(TOP_NAME) $$test -rvtest -cpu_trace -writeappend; \
        fi; \
	done; \
	echo "Total tests run: $$count";

help:
	@echo "Usage: make [target]"
	@echo "Available targets:"
	@echo "  clean             - Remove build artifacts"
	@echo "  verilog           - Generate verilog files"
	@echo "  perf              - Run performance tests"
	@echo "  lab<number>       - Run lab<number>, eg. lab6, lab7, ..."
	@echo "  trace_lab<number> - Run lab<number> with trace, eg. trace_lab6, trace_lab7, ..."

-include ../Makefile