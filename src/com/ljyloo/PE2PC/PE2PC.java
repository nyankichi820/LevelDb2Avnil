package com.ljyloo.PE2PC;

import org.iq80.leveldb.*;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.OldDataLayer;
import net.minecraft.world.level.chunk.storage.*;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class PE2PC {
	private final static int DATALAYER_BITS = 4;
	private final static int BLOCKDATA_BYTES = 4096;
	private final static int METADATA_BYTES = 2048;
	private final static int SKYLIGHTDATA_BYTES = 2048;
	private final static int BLOCKLIGHTDATA_BYTES = 2048;
	
	public static void main(String[] args) throws IOException {
		//System.out.println((-1 % 32 + 32) % 32);
        if (args.length != 2) {
            printUsageAndExit();
        }
        
        File srcFolder;
        try {
            srcFolder = new File(args[0]+"/db");
            if (!srcFolder.exists()) {
                throw new RuntimeException(args[0] + " doesn't exist");
            } else if (!srcFolder.isDirectory()) {
                throw new RuntimeException(args[0] + " is not a folder");
            }
        } catch (Exception e) {
            System.err.println("import folder problem: " + e.getMessage());
            System.out.println("");
            printUsageAndExit();
            return;
        }
        
        File desFolder;
        try {
        	desFolder = new File(args[1]);
            if (!desFolder.exists()) {
                throw new RuntimeException(args[1] + " doesn't exist");
            } else if (!desFolder.isDirectory()) {
                throw new RuntimeException(args[1] + " is not a folder");
            }
        } catch (Exception e) {
            System.err.println("export folder problem: " + e.getMessage());
            System.out.println("");
            printUsageAndExit();
            return;
        }
        
		convert(srcFolder, desFolder);
	}

    private static void printUsageAndExit() {
    	System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        System.out.println("Convert Minecraft: Pocket Edition Maps(LevelDB) to Minecraft Maps(Anvil) or reversely. (c) ljyloo 2017");
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("\tjava -jar Converter.jar <import folder> <export folder>");
        System.out.println("Where:");
        System.out.println("\t<import folder>\tThe full path to the folder containing Minecraft:Pocket Edition world");
        System.out.println("\t<export folder>\tThe full path to the folder which you want to export");
        System.out.println("Example:");
        System.out.println("\tjava -jar Converter.jar /home/ljyloo/import /home/ljyloo/export");
        System.out.println("");
        System.out.println("Visit the homepage of this project for more information:");
        System.out.println("\tgithub.com/ljyloo/LevelDb2Avnil");
        System.exit(1);
    }
	
	public static void convert(File src, File des) throws IOException{
		DB db = null;
		int totalChunk = 0;
		try{
			Options options = new Options();
			options.createIfMissing(true);
			db = factory.open(src, options);
			
			DBIterator iterator = db.iterator();
			//ArrayList<byte[]> keys = new ArrayList<byte[]>();
			HashMap<String, RegionFile> regions = new HashMap<String, RegionFile>();
			HashMap<String, CompoundTag> comChunks = new HashMap<>();
			HashMap<String, Integer> chunkHeight = new HashMap<>();
			try{
				for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()){
					byte[] key = iterator.peekNext().getKey();
					//System.out.println(byte2s(key, false));
					byte[] value = iterator.peekNext().getValue();
					//System.out.println("Length: " + value.length);
					if(key.length == 10 && key[8] == 47){
						
						
						int chunkX = byteArrayToInt(new byte[]{key[3], key[2], key[1], key[0]});
						int chunkZ = byteArrayToInt(new byte[]{key[7], key[6], key[5], key[4]});
						int chunkY = (int) key[9];
						
						System.out.print("\rConverting subchunk X: "+chunkX+" Z: "+chunkZ+" Y: "+chunkY+"                     ");
						System.out.flush();
						totalChunk++;
						
						String comKey = chunkX+","+chunkZ;
						if (!comChunks.containsKey(comKey)){
							//System.out.println("New comChunks");
							CompoundTag tag = new CompoundTag();
							CompoundTag levelData = new CompoundTag();
							tag.put("Level", levelData);
							
							levelData.putByte("LightPopulated", (byte)1);
							levelData.putByte("TerrainPopulated", (byte)1);
							levelData.putByte("V", (byte)1);
							levelData.putInt("xPos", chunkX);
							levelData.putInt("zPos", chunkZ);
							levelData.putLong("InhabitedTime", 0);
							levelData.putLong("LastUpdate", 0);
							
							byte[] biomes = new byte[16 * 16];
							for(int i = 0; i <256; i++)
								biomes[i] = -1;
							levelData.putByteArray("Biomes", biomes);
							
							levelData.put("Entities", new ListTag<CompoundTag>("Entities"));
							
							ListTag<CompoundTag> sectionTags = new ListTag<CompoundTag>("Sections");
							levelData.put("Sections", sectionTags);
							
							levelData.put("TileEntities", new ListTag<CompoundTag>("TileEntities"));
							
							comChunks.put(comKey, tag);
						}
						
						
						
						CompoundTag tag = comChunks.get(comKey);
						CompoundTag levelData = tag.getCompound("Level");
						
						@SuppressWarnings("unchecked")
						ListTag<CompoundTag> sectionTags = (ListTag<CompoundTag>) levelData.getList("Sections");
						
						LevelDBChunk data = new LevelDBChunk(chunkX, chunkZ);
						
						int offset = 1;
						
						data.blocks = new byte[BLOCKDATA_BYTES];
						System.arraycopy(value, offset, data.blocks, 0, BLOCKDATA_BYTES);
						offset += BLOCKDATA_BYTES;
						
						byte[] metadata = new byte[METADATA_BYTES];
						System.arraycopy(value, offset, metadata, 0, METADATA_BYTES);
						offset += METADATA_BYTES;
						data.data = new OldDataLayer(metadata, DATALAYER_BITS);
						
						byte[] skyLightData = new byte[SKYLIGHTDATA_BYTES];
						if (offset + SKYLIGHTDATA_BYTES < value.length)
							System.arraycopy(value, offset, skyLightData, 0, SKYLIGHTDATA_BYTES);
						offset += SKYLIGHTDATA_BYTES;
						data.skyLight = new OldDataLayer(skyLightData, DATALAYER_BITS);
						
						byte[] blockLightData = new byte[BLOCKLIGHTDATA_BYTES];
						if (offset + BLOCKLIGHTDATA_BYTES < value.length)
							System.arraycopy(value, offset, blockLightData, 0, BLOCKLIGHTDATA_BYTES);
						data.blockLight = new OldDataLayer(blockLightData, DATALAYER_BITS);
						
						byte[] blocks = new byte[16 * 16 * 16];
			            DataLayer dataValues = new DataLayer(blocks.length, 4);
			            DataLayer skyLight = new DataLayer(blocks.length, 4);
			            DataLayer blockLight = new DataLayer(blocks.length, 4);

			            for (int x = 0; x < 16; x++) {
			                for (int y = 0; y < 16; y++) {
			                    for (int z = 0; z < 16; z++) {
			                        int pos = (x << 8) | (z << 4) | y;
			                        int block = data.blocks[pos];

			                        blocks[(y << 8) | (z << 4) | x] = (byte) (block & 0xff);
			                        dataValues.set(x, y, z, data.data.get(x, y, z));
			                        //skyLight.set(x, y, z, data.skyLight.get(x, y, z));
			                        //blockLight.set(x, y, z, data.blockLight.get(x, y, z));
			                        skyLight.set(x, y, z, 0xf);
			                        blockLight.set(x, y, z, 0xf);
			                    }
			                }
			            }

			            CompoundTag sectionTag = new CompoundTag();

			            sectionTag.putByte("Y", (byte) (chunkY & 0xff));
			            sectionTag.putByteArray("Blocks", blocks);
			            sectionTag.putByteArray("Data", dataValues.data);
			            sectionTag.putByteArray("SkyLight", skyLight.data);
			            sectionTag.putByteArray("BlockLight", blockLight.data);

			            sectionTags.add(sectionTag);
				        
			            if (!chunkHeight.containsKey(comKey)) {
			            	chunkHeight.put(comKey, chunkY);
			            }
			            else {
			            	int temp = chunkHeight.get(comKey);
			            	if (chunkY > temp)
			            		chunkHeight.put(comKey, chunkY);
			            }
					}
				}
				
				
				
				Iterator<Entry<String, CompoundTag>> iter = comChunks.entrySet().iterator();
				while (iter.hasNext()){
					Entry<String, CompoundTag> entry = iter.next();
					String key = entry.getKey();
					
					CompoundTag tag = entry.getValue();
					CompoundTag levelData = tag.getCompound("Level");
					@SuppressWarnings("unchecked")
					ListTag<CompoundTag> sectionTags = (ListTag<CompoundTag>) levelData.getList("Sections");
					int topChunk = chunkHeight.get(key);
					
					for (int i = 0; i < sectionTags.size(); i++) {
						CompoundTag subChunk = sectionTags.get(i);
						int Y = subChunk.getByte("Y");
						if (Y == topChunk) {
							DataLayer dataValues = new DataLayer(subChunk.getByteArray("Data"), 4);
							
							int[] heightMap = new int[256];
					        for(int x = 0; x < 16; x++){
					        	for(int z = 0; z < 16; z++){
					        		for(int y = 15; y >= 0; y--){
					        			int block = dataValues.get(x, y, z);
					        			if(block != 0){
					        				heightMap[(x << 4) | z] = (Y << 4) | y;
					        				break;
					        			}
					        		}
					        	}
					        }
					        levelData.putIntArray("HeightMap", heightMap);
							break;
						}
					}
					/*
					int[] heightMap = new int[256];
			        for(int x = 0; x < 16; x++){
			        	for(int z = 0; z < 16; z++){
			        		heightMap[(x << 4) | z] = 0;
			        	}
			        }
			        levelData.putIntArray("HeightMap", heightMap);
					*/
					String[] parts = key.split(",");
					int chunkX = Integer.parseInt(parts[0]);
					int chunkZ = Integer.parseInt(parts[1]);
					
					String k = (chunkX >> 5) + "." + (chunkZ >> 5);
					if(!regions.containsKey(k)){
						regions.put(k, new RegionFile(new File(des, "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca")));
					}
					RegionFile regionDest = regions.get(k);
					int regionX = (chunkX % 32 + 32) % 32;
					int regionZ = (chunkZ % 32 + 32) % 32;
					DataOutputStream chunkDataOutputStream = regionDest.getChunkDataOutputStream(regionX, regionZ);
					if(chunkDataOutputStream == null){
						System.out.println(chunkX % 32);
						System.out.println(chunkZ % 32);
					}
					NbtIo.write(tag, chunkDataOutputStream);
					chunkDataOutputStream.close();
				}
			}
			finally{
				Iterator<Entry<String, RegionFile>> iter = regions.entrySet().iterator();
				while (iter.hasNext()){
					Entry<String, RegionFile> entry = iter.next();
					RegionFile region = entry.getValue();
					region.close();
				}
				iterator.close();
			}
		}
		finally{
			db.close();
		}
		if(totalChunk > 0){
			System.out.println("\nDone! totalSubChunks: " + totalChunk);
		}
		else{
			System.out.println("Oops! It seems that the input data does not contain any valid chunk.");
		}
	}
	
	public static String byte2s(byte[] b, boolean ignoreTooLong){
		String s = "0x";
		int length = b.length;
		boolean tooLong = false;
		if(length > 100){
			length = 100;
			tooLong = true;
		}
		for(int i = 0; i < length; i++){
			s = s + b[i] + " ";
		}
		if(tooLong && ignoreTooLong)
			s = s + "...";
		return s;
	}
	
	public static byte[] intToByteArray(int i){
		byte[] result = new byte[4];
		result[0] = (byte)((i >> 24) & 0xFF);
		result[1] = (byte)((i >> 16) & 0xFF);
		result[2] = (byte)((i >> 8) & 0xFF);
		result[3] = (byte)(i & 0xFF);
		return result;
	}
	
	public static int byteArrayToInt(byte[] bytes){
		int value= 0;
		for (int i = 0; i < 4; i++){
			if (bytes.length - i < 1)
				break;
			int shift = (3 - i) * 8;
			value += (bytes[i] & 0x000000FF) << shift;
		}
		return value;
	}
	
	public static class LevelDBChunk{
		public OldDataLayer blockLight;
		public OldDataLayer skyLight;
		public OldDataLayer data;
		public byte[] blocks;
		
		public final int x;
		public final int z;
		
		public LevelDBChunk(int x, int z){
			this.x = x;
			this.z = z;
		}
	}
}