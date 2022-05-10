create_ip -name blk_mem_gen -vendor xilinx.com -library ip -module_name PixelBuffer -dir ${ip_build_dir}
set_property -dict {
	CONFIG.Memory_Type {Simple_Dual_Port_RAM}
	CONFIG.Assume_Synchronous_Clk {true}
	CONFIG.Write_Width_A {8}
	CONFIG.Write_Depth_A {512}
	CONFIG.Read_Width_A {8}
	CONFIG.Operating_Mode_A {NO_CHANGE}
	CONFIG.Enable_A {Always_Enabled}
	CONFIG.Write_Width_B {8}
	CONFIG.Read_Width_B {8}
	CONFIG.Operating_Mode_B {READ_FIRST}
	CONFIG.Enable_B {Always_Enabled}
	CONFIG.Register_PortA_Output_of_Memory_Primitives {false}
	CONFIG.Register_PortB_Output_of_Memory_Primitives {false}
	CONFIG.Port_B_Clock {100}
	CONFIG.Port_B_Enable_Rate {100}
} [get_ips PixelBuffer]
