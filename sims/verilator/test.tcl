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
    set dasm_io [open "|/home/david/git/chipyard/riscv-tools-install/bin/spike-dasm" r+]
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
    puts "${indent}seq: ${seq}"
    puts "${indent}pc: ${pc}"
    puts "${indent}inst: ${inst} - [dasm $inst]"
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
        for {set i $head} {$i != $tail} {set i [expr {[expr {$i+1}] % 8 }]} {
            printUop "${base_name}.q_uop_$i" $time "${indent}    "
            puts "${indent}---------------------------------"
        }
    }
}

proc printState {time} { 
    puts "time: $time"
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue" "A-queue" "  "
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    printQueue $time "TOP.TestHarness.top.boom_tile.core.dispatcher.b_queue" "B-queue" "  "
    puts "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
}

#set ist [getIST 128]
#puts "$ist"
#puts "done"
#printUop "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue.io_enq_uops_0_bits" [gtkwave::getMarker]
#printQueue [gtkwave::getMarker] "TOP.TestHarness.top.boom_tile.core.dispatcher.a_queue" "A-queue"
#puts [valueAtTime ]
printState [gtkwave::getMarker]

