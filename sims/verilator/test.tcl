proc addIST {size} {
    set lst [list]
    for {set i 0} {$i < $size} {incr i} {
        lappend lst "TOP.TestHarness.top.boom_tile.core.ist.tag_table_$i\[39:0\]"
        lappend lst "TOP.TestHarness.top.boom_tile.core.ist.tag_valids_$i"
    }
    gtkwave::addSignalsFromList $lst
    return $lst
}

proc getIST {size} {
    # using existing traces only seems to work if they are visible - so we add them instead and remove them afterwards
    # to preserve the other traces 
    # we select the first trace in order for the insert/remove to happen at the top
    gtkwave::setTraceHighlightFromIndex 0 on
    # a gui update is needed here for this to work...
    gtkwave::nop
    # add needed traces
    set trace_lst [addIST $size]
    set ist [list]
    for {set i 0} {$i < $size} {incr i} {
        set valid [ gtkwave::getTraceValueAtMarkerFromName "TOP.TestHarness.top.boom_tile.core.ist.tag_valids_$i" ]
        #puts $valid
        if {$valid == "1" } {
            set entry [ gtkwave::getTraceValueAtMarkerFromName "TOP.TestHarness.top.boom_tile.core.ist.tag_table_$i\[39:0\]" ]
            lappend ist $entry
        }
    }
    # delete first occurence of traces
    gtkwave::deleteSignalsFromList $trace_lst
    return [lsort $ist]
}

#set ist [getIST 128]
#puts "$ist"
#puts "done"
lassign [gtkwave::signalChangeList "TOP.TestHarness.top.boom_tile.core.ist.tag_table_0\[39:0\]" -start_time 123456 -max 1] dont_care value
puts $value
