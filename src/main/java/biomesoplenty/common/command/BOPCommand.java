/*******************************************************************************
 * Copyright 2015, the Biomes O' Plenty Team
 * 
 * This work is licensed under a Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International Public License.
 * 
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/4.0/.
 ******************************************************************************/

package biomesoplenty.common.command;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import biomesoplenty.common.util.biome.BiomeUtils;

import com.google.common.collect.Lists;

public class BOPCommand extends CommandBase
{
    @Override
    public String getCommandName()
    {
        return "biomesoplenty";
    }
    
    @Override
    public List getCommandAliases()
    {
        return Lists.newArrayList("bop", "biomesop");
    }

    @Override
    public String getCommandUsage(ICommandSender sender)
    {
        return "commands.biomesoplenty.usage";
    }
    
    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 1)
        {
            throw new WrongUsageException("commands.biomesoplenty.usage");
        }
        else if ("biomename".equals(args[0]))
        {
            getBiomeName(sender, args);
        }
        else if ("tpbiome".equals(args[0]))
        {
            teleportFoundBiome(sender, args);
        }
        else if ("stripchunk".equals(args[0]))
        {
            stripChunk(sender, args);
        }
    }
    
    private void getBiomeName(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 2)
        {
            throw new WrongUsageException("commands.biomesoplenty.biomename.usage");
        }
        
        int biomeId = parseInt(args[1], 0, 255);
        BiomeGenBase biome = BiomeGenBase.getBiome(biomeId);
        
        sender.addChatMessage(new ChatComponentTranslation("commands.biomesoplenty.biomename.success", biomeId, biome == null ? "Undefined" : biome.biomeName));
    }
    
    private void teleportFoundBiome(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 2)
        {
            throw new WrongUsageException("commands.biomesoplenty.tpbiome.usage");
        }
        
        int biomeId = parseInt(args[1], 0, 255);
        BiomeGenBase biome = BiomeGenBase.getBiome(biomeId);
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        World world = player.worldObj;
        BlockPos closestBiomePos = biome == null ? null : BiomeUtils.findBiome(world, biome, player.getPosition());
        
        if (closestBiomePos != null)
        {
            double x = (double)closestBiomePos.getX();
            double y = (double)world.getTopSolidOrLiquidBlock(closestBiomePos).getY();
            double z = (double)closestBiomePos.getZ();
            
            player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
            sender.addChatMessage(new ChatComponentTranslation("commands.biomesoplenty.tpbiome.success", player.getCommandSenderName(), biome.biomeName, x, y, z));
        }
        else
        {
            sender.addChatMessage(new ChatComponentTranslation("commands.biomesoplenty.tpbiome.error", biome == null ? "Undefined" : biome.biomeName));
        }
    }
    
    private void stripChunk(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 5)
        {
            throw new WrongUsageException("commands.biomesoplenty.stripchunk.usage");
        }

        int radius = parseInt(args[1]);
        String mode = args[2];
        boolean blacklist;

        if (mode.equals("include")) blacklist = false;
        else if (mode.equals("exclude")) blacklist = true;
        else
        {
            throw new WrongUsageException("commands.biomesoplenty.stripchunk.usage");
        }

        List<IBlockState> stateList = Lists.newArrayList();

        for (int i = 0; i < (args.length - 3) / 2; i++)
        {
            Block block = getBlockByText(sender, args[i * 2 + 3]);
            int metadata = parseInt(args[i * 2 + 3 + 1]);

            stateList.add(block.getStateFromMeta(metadata));
        }

        BlockPos playerPos = sender.getPosition();
        World world = sender.getEntityWorld();
        
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        for (int chunkX = -radius; chunkX < radius; chunkX++)
        {
            for (int chunkZ = -radius; chunkZ < radius; chunkZ++)
            {
                Chunk chunk = world.getChunkFromChunkCoords(playerChunkX + chunkX, (playerChunkZ + chunkZ));
                
                for (ExtendedBlockStorage blockStorage : chunk.getBlockStorageArray())
                {
                    if (blockStorage != null)
                    {
                        for (int x = 0; x < 16; x++)
                        {
                            for (int y = 0; y < 16; y++)
                            {
                                for (int z = 0; z < 16; z++)
                                {
                                    BlockPos pos = new BlockPos(chunk.xPosition * 16 + x, blockStorage.getYLocation() + y, chunk.zPosition * 16 + z);
                                    IBlockState state = blockStorage.get(x, y, z);
                                    
                                    if (((!blacklist && stateList.contains(state)) || (blacklist && !stateList.contains(state))) && pos.getY() > 0)
                                    {
                                        blockStorage.set(x, y, z, Blocks.air.getDefaultState());
                                        world.markBlockForUpdate(pos);
                                        world.notifyNeighborsRespectDebug(pos, Blocks.air);
                                    }
                                }
                            }
                        }
                    }
                }
                
                chunk.generateSkylightMap();
            }
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, "biomename", "tpbiome", "stripchunk");
        }
        else if (args.length == 3)
        {
            return getListOfStringsMatchingLastWord(args, "include", "exclude");
        }
        
        return null;
    }
}