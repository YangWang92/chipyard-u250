proc addIST {size} {
    set lst [list]
    for {set i 0} {$i < $size} {incr i} {
        lappend lst "TOP.TestHarness.top.boom_tile.core.ist.tag_table_$i\[39:0\]"
        lappend lst "TOP.TestHarness.top.boom_tile.core.ist.tag_valids_$i"
    }
    gtkwave::addSignalsFromList $lst
    return $lst
}

# uses marker time as default
proc valueAtTime {name {time ""}} {
    if {$time eq ""} {
        set time [gtkwave::getMarker]
    }
    # get a list of time, new value pairs of length one 
    lassign [gtkwave::signalChangeList $name -start_time $time -max 1] dont_care value
#    puts "vat name: $name"
#    gtkwave::addSignalsFromList [list $name]
    return $value
}

proc getIST {size} {
    set ist [list]
    set time [gtkwave::getMarker]
    for {set i 0} {$i < $size} {incr i} {
        set valid [ valueAtTime "TOP.TestHarness.top.boom_tile.core.ist.tag_valids_$i" $time]
        #puts $valid
        if {$valid == "1" } {
            set entry [ valueAtTime "TOP.TestHarness.top.boom_tile.core.ist.tag_table_$i" $time]
            lappend ist $entry
        }
    }
    return [lsort $ist]
}

proc dasm {inst} {
    set riscv_dir $::env(RISCV)
    # starts a new dasm process - not ideal but works
    set dasm_io [open "|${riscv_dir}/bin/spike-dasm" r+]
    puts $dasm_io "DASM($inst)"
    flush $dasm_io
    gets $dasm_io line
    close $dasm_io
    return $line
}

# base name is the uop withour trailing _
proc printUop {base_name time {indent ""}} {
    set inst [ valueAtTime "${base_name}_debug_inst" $time]
    set pc [ valueAtTime "${base_name}_debug_pc" $time]
    set seq [ expr [valueAtTime "${base_name}_debug_events_fetch_seq" $time]]
    set iq_type [ expr [valueAtTime "${base_name}_iq_type" $time]]
    set prs1_busy [ expr [valueAtTime "${base_name}_prs1_busy" $time]]
    set prs2_busy [ expr [valueAtTime "${base_name}_prs2_busy" $time]]
    puts "${indent}seq: ${seq}"
    puts "${indent}pc: ${pc}"
    puts "${indent}iq-type: ${iq_type}"
    puts "${indent}inst: ${inst} - [dasm $inst]"
    puts "${indent}prs1_busy: ${prs1_busy}"
    puts "${indent}prs2_busy: ${prs2_busy}"
}

proc printQueue {time base_name name {indent ""}} {
    set empty [valueAtTime "${base_name}.empty" $time]
    set deq [valueAtTime "${base_name}.io_deq_uop" $time]
    puts "${indent}${name} - empty: $empty - deq: $deq"
    puts "${indent}enqueuing: ================================="
    for {set i 0} {$i < 4} {incr i} {
        set valid [valueAtTime "${base_name}.io_enq_uops_${i}_valid" $time]
        if {$valid == "1" } {
            printUop "${base_name}.io_enq_uops_${i}_bits" $time "${indent}   "
            puts "${indent}---------------------------------"
        }
    }
    puts "${indent}in queue: ================================="
    
    if {$empty == "0"} {
        set head [expr [valueAtTime "${base_name}.head" $time]]
        set tail [expr [valueAtTime "${base_name}.tail" $time]]
#        puts "head: $head tail: $tail"
        # ugly workaround for full queue
        if {$head == $tail} {
            printUop "${base_name}.q_uop_${head}" $time "${indent}    "
            puts "${indent}---------------------------------"
            set head [expr {($head+1) % 8 }]
        }
        for {set i $head} {$i != $tail} {set i [expr {($i+1) % 8 }]} {
            printUop "${base_name}.q_uop_${i}" $time "${indent}    "
            puts "${indent}---------------------------------"
        }
    }
}

proc printIssueSlot {time base_name name {indent ""}} {
    set p1 [valueAtTime "${base_name}.p1" $time]
    set p2 [valueAtTime "${base_name}.p2" $time]
    set request [valueAtTime "${base_name}.io_request" $time]
    set grant [valueAtTime "${base_name}.io_grant" $time]
    set state [valueAtTime "${base_name}.state" $time]
    puts "${indent}${name} - state: $state - p1: $p1 - p2: $p2 - request: $request - grant: $grant"
    puts "${indent}incoming: ================================="
    set in_valid [valueAtTime "${base_name}.io_in_uop_valid" $time]
    if {$in_valid == "1" } {
        printUop "${base_name}.io_in_uop_bits" $time "${indent}   "
        puts "${indent}---------------------------------"
    }
    puts "${indent}current: ================================="
    if {$state == "0b01" } {
        printUop "${base_name}.slot_uop" $time "${indent}   "
        puts "${indent}---------------------------------"
    }
}

proc printState {time} { 
#    puts "time: $time"
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue" "A-queue" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.b_queue" "B-queue" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"

    # single issue lsc
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.int_issue_unit.slots_0" "A-int" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.mem_issue_unit.slots_0" "A-mem" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.fp_pipeline.fp_issue_unit.slots_0" "A-fp" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.int_issue_unit.slots_1" "B-int" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.mem_issue_unit.slots_1" "B-mem" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"

    # unified stuff:
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.unified_issue_unit.slots_0" "A-intmem" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.unified_issue_unit.slots_1" "B-intmem" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
#    printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.fp_pipeline.fp_issue_unit.slots_0" "A-fp" "  "
#    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"

    #DnB
    puts "time: $time"
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.crq" "CRQ" "  "
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.dlq" "DLQ" "  "
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    for {set i 0} {$i < 8} {incr i} {
        printIssueSlot $time "TOP.TestHarness.top.boom_tile.core.dnb_issue_unit.slots_$i" "IQ$i" "  "
        puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    }
}

#set ist [getIST 128]
#puts "$ist"
#puts "done"
#printUop "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue.io_enq_uops_0_bits" [gtkwave::getMarker]
#printQueue [gtkwave::getMarker] "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue" "A-queue"
#puts [valueAtTime ]
printState [gtkwave::getMarker]
#printIssueSlot [gtkwave::getMarker] "TOP.TestHarness.top.boom_tile.core.int_issue_unit.slots_0" "A-int"
