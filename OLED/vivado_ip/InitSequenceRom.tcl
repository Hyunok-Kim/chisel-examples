create_ip -name blk_mem_gen -vendor xilinx.com -library ip -module_name InitSequenceRom -dir ${ip_build_dir}
set_property -dict {
	CONFIG.Memory_Type {Single_Port_ROM} 
	CONFIG.Enable_A {Always_Enabled} 
	CONFIG.Register_PortA_Output_of_Memory_Primitives {false} 
	CONFIG.Load_Init_File {true}
	CONFIG.Coe_File {../../../vivado_ip/init_sequence.coe}
	CONFIG.Port_A_Write_Rate {0}
} [get_ips InitSequenceRom]
