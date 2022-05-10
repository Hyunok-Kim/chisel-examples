create_ip -name blk_mem_gen -vendor xilinx.com -library ip -module_name CharLib -dir ${ip_build_dir}
set_property -dict {
	CONFIG.Memory_Type {Single_Port_ROM}
	CONFIG.Write_Width_A {8}
	CONFIG.Write_Depth_A {1024}
	CONFIG.Read_Width_A {8}
	CONFIG.Enable_A {Always_Enabled}
	CONFIG.Write_Width_B {8}
	CONFIG.Read_Width_B {8}
	CONFIG.Register_PortA_Output_of_Memory_Primitives {false}
	CONFIG.Load_Init_File {true}
	CONFIG.Coe_File {../../../vivado_ip/characterLib.coe}
	CONFIG.Port_A_Write_Rate {0}
} [get_ips CharLib]

