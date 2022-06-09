package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Part;
import com.xilinx.rapidwright.device.PartNameTools;
import com.xilinx.rapidwright.util.FileTools;
import org.python.google.common.collect.Lists;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


public class CreatePblocks {

    public static Device dev = null;
    public static int partitionNumber = 6; //number of partitions defined a priori
    public static int groupNumber = 0;     //number of trees for each group
    public static int lutCount = 0;
    public static int regCount = 0;
    public static int lutRAMCount = 0;
    public static int dspCount = 0;
    public static int carryCount = 0;
    public static int bram18kCount = 0;
    public static int bram36kCount = 0;
    public static final int LUTS_PER_CLE = 8;
    public static final int FF_PER_CLE = 16;
    public static final float CLES_PER_BRAM = 5;
    public static final float CLES_PER_DSP = 2.5f;
    public static int RAMLUTS_PER_CLE = 8;
    public static int CARRY_PER_CLE = 1;
    public static int SLICES_PER_TILE = 1;

    private static final String TREES = "tree_rm_._._inst_._tree_cl._._._._synth_.";
    private static final String SHAPE_REPORT = "/home/locav/shape.txt";

    private static void getResourceUsage(String reportFileName){
        ArrayList<String> lines = FileTools.getLinesFromTextFile(reportFileName);

        for(String line : lines){
            if(line.startsWith("| Device")){
                String partName = line.split("\\s+")[3];
                Part part = PartNameTools.getPart(partName);
                dev = Device.getDevice(part);
                if(dev == null){
                    throw new RuntimeException("ERROR: Couldn't load device for part: " +
                            line.split("\\s+")[3] + " (" +  partName + ")");
                }
            }else if(line.startsWith("| CLB LUTs") || line.startsWith("| Slice LUTs")){
                lutCount = Integer.parseInt(line.split("\\s+")[4]);
            }else if(line.startsWith("| CLB Registers") || line.startsWith("| Slice Registers")){
                regCount = Integer.parseInt(line.split("\\s+")[4]);
            }else if(line.startsWith("|   LUT as Memory")){
                lutRAMCount = Integer.parseInt(line.split("\\s+")[5]);
            }else if(line.startsWith("|   RAMB36/FIFO")){
                bram36kCount = Integer.parseInt(line.split("\\s+")[3]);
            }else if(line.startsWith("|   RAMB18")){
                bram18kCount = Integer.parseInt(line.split("\\s+")[3]);
            }else if(line.startsWith("| DSPs")){
                dspCount = Integer.parseInt(line.split("\\s+")[3]);
            }else if(line.startsWith("| CARRY")){
                carryCount = Integer.parseInt(line.split("\\s+")[3]);
            }
        }
            //System.out.println("Parsed report: " + reportFileName);
            //System.out.println("  Device: " + dev.getName());
            System.out.println("    LUTs: " + lutCount);
            System.out.println("    REGs: " + regCount);
            //System.out.println("    DSPs: " + dspCount);
            //System.out.println("18kBRAMs: " + bram18kCount);
            //System.out.println("36kBRAMs: " + bram36kCount);

        if(lutCount < LUTS_PER_CLE) lutCount = LUTS_PER_CLE;
    }
    public static int sliceNumber(String reportFileName) {
        getResourceUsage(reportFileName);


        // Let's calculate exactly how many sites we need of each type
        int slicesLUTSRequired = Math.round((((float) (lutCount - lutRAMCount) / (float) LUTS_PER_CLE) * SLICES_PER_TILE * 1) + 0.5f); // Multiply with SLICES_PER_TILE for 7 series, where one CLB has 2 slices
        // Let's calculate how many FF & carry slices we need
        int slicesFFCarryRequired = Math.round((((float) regCount / (float) FF_PER_CLE) * SLICES_PER_TILE * 1) + 0.5f);
        if (carryCount / CARRY_PER_CLE > slicesFFCarryRequired / SLICES_PER_TILE) {
            slicesFFCarryRequired = Math.round((((float) carryCount / (float) CARRY_PER_CLE) * SLICES_PER_TILE * 1) + 0.5f);
        }
        int dspsRequired = dspCount;
        if ((dspsRequired & 0x1) == 0x1) {
            // if we have an odd number of dsp, round up by 1 more
            dspsRequired++;
        }
        int ramb36sRequired = bram36kCount + (int) Math.ceil((float) bram18kCount / 2.0);
        int sliceMsRequired = (int) Math.ceil((float) lutRAMCount / (float) RAMLUTS_PER_CLE); // Not multiplying with SLICES_PER_TILE, as in one M-CLB Tile, there is only one slice having LUTRAM

        // now compute Nr slices. If nr of FF & carry slices is smaller than current total nr of slices given by LUTs, than these could be mapped in the same slices.
        // Update if more slices are needed for FF & carry
        int slicesRequired = slicesLUTSRequired;
        if (slicesFFCarryRequired > (slicesLUTSRequired + sliceMsRequired)) {
            slicesRequired += (slicesFFCarryRequired - (slicesLUTSRequired + sliceMsRequired));
        }

        System.out.println("Slices required: " + slicesRequired); //print slices required
        return slicesRequired;
    }

    int slicesNumber;
    String pblockCoordinates;
    String treeName;

    @Override
    public String toString() {
        return "\t" + treeName + "  \t" + pblockCoordinates + "  \t" + slicesNumber + " \t";
    }



    public static void main(String[] args){

        HashMap<String, Integer> tree = new HashMap<>();
        TreeMap<Integer, CreatePblocks> groups = new TreeMap<>();
        List<CreatePblocks> list = new ArrayList<CreatePblocks>();

        int cn = 0;
        if (args.length != 1) {
            System.out.println("USAGE: <dir_to_project_runs>");
            return;
        }

        File dir = new File(args[0]);
        if (dir.exists() && dir.isDirectory()){
            for (File child : dir.listFiles()){
                if (child.getName().matches(TREES)) {
                    //System.out.println(child.getName().matches(TREES));
                    String shapeFile = SHAPE_REPORT;
                    String utilReportFile = null;
                    for (File file : child.listFiles()) {
                        if (file.getAbsoluteFile().getName().endsWith("_utilization_synth.rpt")) {
                            utilReportFile = file.toString();
                        }
                    }
                    System.out.println("\n");
                    if (utilReportFile != null) {
                        System.out.println(child.getName());
                        //groups.put(sliceNumber(utilReportFile) , child.getName());
                        //getResourceUsage(utilReportFile);
                        CreatePblocks block = new CreatePblocks();

                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        System.setOut(new PrintStream(buffer));

                        PBlockGenerator.main(new String[]{
                                "-u", utilReportFile,
                                "-s", shapeFile,
                                "-c", "1",     // number of pblocks
                                "-a", "0.016", // aspect_ratio = 1/60 (one column)
                                "-p", "/home/locav/automatic_floorplanner/Quick_AlternateCLBRouting_1_pblock0_/routed.dcp", // dcp to avoid overlap
                                "-o", "1.0"  // overhead (inside the pblock area)
                        });

                        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));

                        block.pblockCoordinates = buffer.toString().trim();
                        buffer.reset();

                        block.treeName = child.getName();
                        block.slicesNumber = sliceNumber(utilReportFile);
                        System.out.println("\n 1) " + block.slicesNumber + "\n 2) " + block.pblockCoordinates + " \n 3) " + block.treeName);
                        groups.put(cn++, block);
                        list.add(block);
                        //tree.put(child.getName(), sliceNumber(utilReportFile));

                    }
                }
            }


        }

        //Sum = total number of trees; count = number of different sizes
        int count = (int) tree.values().stream().distinct().count();
        int sum = (int) tree.values().stream().count();
        System.out.println("\n\nSum: " + sum);
        System.out.println("Unique sizes: " + count);

        groupNumber = sum/partitionNumber;


        //sorted map
        HashMap<String, Integer> sortedTree = tree.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        System.out.println("\n\n");
            for (String i : sortedTree.keySet()){
                System.out.println("tree:  " + i + "     |    slices required:  " + sortedTree.get(i));
            }


        //grouped trees by size (useless: unbalanced groups)
        Map<Integer, List<String>> valueMap = sortedTree.keySet().stream().collect(Collectors.groupingBy(k -> sortedTree.get(k)));
        //System.out.println(valueMap);


        HashMap<Integer, CreatePblocks> resultSet = groups.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().slicesNumber))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        //System.out.println(resultSet.keySet());
        list.sort(Comparator.comparing(a -> a.slicesNumber));
        //list.forEach(System.out::println);



        //split the maps
        //prova ad aggiungere un valore e dire ad ogni elemento a chi appartiene il blocco
        List<List<CreatePblocks>> smallerLists = Lists.partition(list, 7);
        //smallerLists.forEach(System.out::println);

        //prendo il più grande dei tree di ogni gruppo

        List<CreatePblocks> biggestTrees = new ArrayList<>();
        smallerLists.forEach(l -> biggestTrees.add(l.get(l.size() - 1)));
        biggestTrees.forEach(System.out::println);

        //avoid overlapping





        //create xdc

        //create_pblock pblock_tree_rp_0_0
        //add_cells_to_pblock [get_pblocks pblock_tree_rp_0_0] [get_cells -quiet [list top_design_i/tree_rp_0_0]]
        //resize_pblock [get_pblocks pblock_tree_rp_0_0] -add {SLICE_X5Y60:SLICE_X8Y119}
        //set_property SNAPPING_MODE ON [get_pblocks pblock_tree_rp_0_0]

        String pblockName = "Princess";
        String pblockCoordinates  = "Luna";
        String text = String.format(
                "create_pblock pblock_tree_rp_0_0 %s %s!\n" +
                "add_cells_to_pblock [get_pblocks pblock_tree_rp_0_0] [get_cells -quiet [list top_design_i/tree_rp_0_0]\n" +
                "resize_pblock [get_pblocks pblock_tree_rp_0_0] -add {SLICE_X5Y60:SLICE_X8Y119}\n" +
                "set_property SNAPPING_MODE ON [get_pblocks pblock_tree_rp_0_0]\n",
                pblockName, pblockCoordinates);


    }
}


