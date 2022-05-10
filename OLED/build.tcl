proc _do_impl {jobs {strategies ""}} {
	if {![llength $strategies]} {
		launch_runs impl_1 -to_step write_bitstream -jobs $jobs
		wait_on_run impl_1
	} else {
		set impl_runs "impl_1"
		set_property STRATEGY "[lindex $strategies 0]" [get_runs impl_1]
		for {set i 1} {$i < [llength $strategies]} {incr i 1} {
			set r impl_[expr $i + 1]
			set s [lindex $strategies $i]
			create_run $r -flow {Vivado Implementation 2020} -parent_run synth_1 -strategy "$s"
			lappend impl_runs $r
		}
		launch_runs $impl_runs -to_step write_bitstream -jobs $jobs
		foreach r $impl_runs {
			wait_on_run $r
		}
	}
}

array set build_options {
	-board "nexys_video"
	-jobs 4
	-synth_ip 1
	-impl 0
}

# Expect arguments in the form of `-argument value`
for {set i 0} {$i < $argc} {incr i 2} {
	set arg [lindex $argv $i]
	set val [lindex $argv [expr $i+1]]
	if {[info exists build_options($arg)]} {
		set build_options($arg) $val
		puts "Set build option $arg to $val"
	} else {
		puts "Skip unknown argument $arg and its value $val"
	}
}
# Settings based on defaults or passed in values
foreach {key value} [array get build_options] {
	set [string range $key 1 end] $value
}

set root_dir [file normalize ..]
source  ${root_dir}/../board_settings/${board}.tcl
set build_dir [file normalize .]

# Create/open Manage IP project
set ip_build_dir ${build_dir}/vivado_ip
if {![file exists ${ip_build_dir}/manage_ip/]} {
	puts "INFO: \[Manage IP\] Creating Manage IP project..."
	create_project -force manage_ip ${ip_build_dir}/manage_ip -part $part -ip
	if {![string equal $board_part ""]} {
		set_property BOARD_PART $board_part [current_project]
	}
	set_property simulator_language verilog [current_project]
} else {
	puts "INFO: \[Manage IP\] Opening existing Manage IP project..."
	open_project -quiet ${ip_build_dir}/manage_ip/manage_ip.xpr
}

# Run synthesis for each IP
set ip_dict [dict create]
set ip_tcl_dir ${root_dir}/vivado_ip
source ${ip_tcl_dir}/vivado_ip.tcl
foreach ip $ips {
	# Pre-save IP name and its build directory to a global dictionay
	dict append ip_dict $ip ${ip_build_dir}/${ip}

	# Remove IP that does not exists in the project, which may have been
	# deleted by the user and needs to be regenerated
	if {[string equal [get_ips -quiet $ip] ""]} {
		export_ip_user_files -of_objects [get_files ${ip_build_dir}/${ip}/${ip}.xci] -no_script -reset -force -quiet
		remove_files -quiet [get_files ${ip_build_dir}/${ip}/${ip}.xci]
		file delete -force ${ip_build_dir}/${ip}
	}

	# - IP directory does not exist
	set cached [file exists ${ip_build_dir}/${ip}]
	if {$cached} {
		puts "INFO: \[$ip\] Use existing IP build"
		continue
	}

	source ${ip_tcl_dir}/${ip}.tcl

	upgrade_ip [get_ips $ip]
	generate_target synthesis [get_ips $ip]

	# Run out-of-context IP synthesis
	if {$synth_ip} {
		create_ip_run [get_ips $ip]
		launch_runs ${ip}_synth_1
		wait_on_run ${ip}_synth_1
	}
}
close_project

set top OLED
set top_build_dir ${build_dir}/${top}
create_project -force $top $top_build_dir -part $part
if {![string equal $board_part ""]} {
	set_property BOARD_PART $board_part [current_project]
}
set_property target_language verilog [current_project]

dict for {ip ip_dir} $ip_dict {
	read_ip -quiet ${ip_dir}/${ip}.xci
}

read_verilog -quiet [glob -nocomplain -directory ${root_dir} "*.v"]
read_xdc -quiet ${root_dir}/constr/OLED.xdc

# Implement design
if {$impl} {
	update_compile_order -fileset sources_1
	_do_impl $jobs {"Vivado Implementation Defaults"}
}
