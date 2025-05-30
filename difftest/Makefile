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
	rm -rf core/PuaCpu.v

perf: obj_dir/V$(TOP_NAME)
	$(call git_commit, "perf test RTL") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in ./test/bin/riscv-test/benchmarks/*; do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc -perf; \
	done; \

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

lab1: obj_dir/V$(TOP_NAME)
	$(call git_commit, "test lab1") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab1.bin -rvtest -initgprs -trace 10000000 -pc

trace_lab1: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace lab1") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab1.bin -rvtest -initgprs -cpu_trace

TESTS234 := lab2 lab3 lab4
TRACE_TESTS234 := $(addprefix trace_,$(TESTS234))

$(TESTS234): %: obj_dir/V$(TOP_NAME)
	$(call git_commit, "test $@") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/$@.bin -rvtest -trace 10000000 -pc

$(TRACE_TESTS234): trace_%: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace $*") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/$*.bin -rvtest -cpu_trace

lab5: obj_dir/V$(TOP_NAME)
	$(call git_commit, "test lab5") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab5.bin -rvtest -trace 10000000 -pc -hasdelayslot

trace_lab5: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace lab5") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab5.bin -rvtest -cpu_trace -hasdelayslot

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

lab8: obj_dir/V$(TOP_NAME)
	$(call git_commit, "test lab8") # DO NOT REMOVE THIS LINE!!!
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab8.bin -rvtest -trace 10000000 -pc -onlymodem

trace_lab8: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace lab8") # DO NOT REMOVE THIS LINE!!!
	rm -rf ./trace.txt
	./obj_dir/V$(TOP_NAME) ./test/bin/lab-test/lab8.bin -rvtest -cpu_trace -onlymodem

lab9: obj_dir/V$(TOP_NAME)
	$(call git_commit, "test lab9") # DO NOT REMOVE THIS LINE!!!
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64ui-p-fence_i|*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -pc; \
	done; \
	echo "Total tests run: $$count";
	$(MAKE) qbench

trace_lab9: obj_dir/V$(TOP_NAME)
	$(call git_commit, "trace lab9") # DO NOT REMOVE THIS LINE!!!
	rm -rf ./trace.txt
	count=0; \
	for test in $$(find ./test/bin/riscv-test/ \( -name "*rv64ui-p-*" -o -name "*rv64um-p-*" -o -name "*rv64mi-p-*" \) | sort | grep -vE "*rv64ui-p-fence_i|*rv64mi-p-access"); do \
		count=$$((count + 1)); \
		echo "Running test $$count: $$test"; \
		./obj_dir/V$(TOP_NAME) $$test -rvtest -cpu_trace -writeappend; \
	done; \
	echo "Total tests run: $$count";

help:
	@echo "Usage: make [target]"
	@echo "Available targets:"
	@echo "  clean             - Remove build artifacts"
	@echo "  verilog           - Generate verilog files"
	@echo "  perf              - Run performance tests"
	@echo "  lab<number>       - Run lab<number>, eg. lab1, lab2, ..."
	@echo "  trace_lab<number> - Run lab<number> with trace, eg. trace_lab1, trace_lab2, ..."

-include ../Makefile