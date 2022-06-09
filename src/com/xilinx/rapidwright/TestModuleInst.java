
package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.device.Site;


public class TestModuleInst {
    public static void main(String[] args){

        String designName = "top";
        String partName = "xczu3eg-sbva484-1-i";
        Design d = new Design(designName, partName);
        Design p0Design = Design.readCheckpoint("/home/locav/automatic_floorplanner/Quick_AlternateCLBRouting_1_pblock0_/routed.dcp");
        Design p1Design = Design.readCheckpoint("/home/locav/automatic_floorplanner/Quick_AlternateCLBRouting_1_pblock0/routed.dcp");
        Module p0Module = new Module(p0Design, "/home/locav/automatic_floorplanner/Quick_AlternateCLBRouting_1_pblock0_/routed_0_metadata.txt");
        Module p1Module = new Module(p1Design, "/home/locav/automatic_floorplanner/Quick_AlternateCLBRouting_1_pblock0/routed_0_metadata.txt");
        ModuleInst mi0 = d.createModuleInst("p0", p0Module);
        ModuleInst mi1 = d.createModuleInst("p1", p1Module);
        d.getNetlist().migrateCellAndSubCells(p0Module.getNetlist().getTopCell());
        d.getNetlist().migrateCellAndSubCells(p1Module.getNetlist().getTopCell());
        Site anchor0 = p0Module.getAnchor().getSite();
        Site anchor1 = p1Module.getAnchor().getSite();
        mi0.place(anchor0);
        mi1.place(anchor1);
        d.writeCheckpoint("test.dcp");

    }
}
