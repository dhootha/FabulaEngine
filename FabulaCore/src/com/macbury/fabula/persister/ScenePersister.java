package com.macbury.fabula.persister;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;
import org.simpleframework.xml.core.Complete;
import org.simpleframework.xml.core.Persist;

import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.macbury.fabula.map.Scene;
import com.macbury.fabula.terrain.Terrain;
import com.macbury.fabula.terrain.foliage.Foliage;
import com.macbury.fabula.terrain.tile.Tile;
import com.macbury.fabula.terrain.tileset.AutoTiles;
import com.macbury.fabula.terrain.water.Water;

@Root(name="scene")
public class ScenePersister {
  public static final int VERSION = 3;  
  @Element(required=false)
  private int version;
  @Element
  private String           name;
  @Element(required=false)
  private String           skybox;
  @Element
  private String           uid;
  @Element
  private String           finalShader;
  @Element
  private int              ambientColor;
  @Element
  private int              sunLightColor;
  
  @Element
  private int              columns;
  @Element
  private int              rows;
  @Element
  private String           tilesetName;
  @Element
  private String           foliageName;
  @Element
  private String           terrainData;
  
  @Element(required=false)
  private WaterPersister waterData;
  @Element(required=false)
  private FoliagePersister foliageData;
  private Scene scene;
  private Terrain terrain;
  
  private boolean skipLoadingTerrainData;
  private int uncompressedSize;
  private int compressedSize;
  
  public ScenePersister() {
    
  }
  
  public ScenePersister(Scene scene) {
    this.scene   = scene;
    this.terrain = scene.getTerrain();
    this.rows    = terrain.getRows();
    this.columns = terrain.getColumns();
    this.name    = scene.getName();
    this.finalShader = scene.getFinalShader();
    this.uid     = scene.getUID();
    if (scene.getSkybox() != null) {
      this.skybox  = scene.getSkybox().getName();
    }
    
    tilesetName     = terrain.getTileset().getName();
    foliageName     = terrain.getFoliageSet().getName();
    ambientColor    = scene.getLights().ambientLight.toIntBits();
    sunLightColor   = scene.getSunLight().color.toIntBits();
    this.version    = VERSION;
  }
  
  @Commit
  public void load() {
    if (skipLoadingTerrainData) {
      terrainData = null;
      return;
    }
    
    if (version != VERSION) {
      throw new GdxRuntimeException("Map is old! Map version is " + version + " but required is " + VERSION);
    }
    
    Inflater inflater = new Inflater();
    //inflater.setStrategy(Deflater.BEST_SPEED);
    
    this.scene = new Scene(this.name, this.uid, this.columns, this.rows);
    this.scene.setFinalShader(finalShader);
    this.scene.setSkyboxName(skybox);
    
    Water water = scene.getWater();
    if (waterData != null) {
      water.setAlpha(waterData.alpha);
      water.setMix(waterData.mix);
      water.setAmplitudeWave(waterData.amplitude);
      water.setWaterAnimationSpeed(waterData.animationSpeed);
      water.setAngleWaveSpeed(waterData.speed);
      water.setWaterTexture(waterData.material);
    }
    
    Foliage foliage = scene.getFoliage();
    if (foliageData != null) {
      foliage.setAmplitude(foliageData.amplitude);
      foliage.setSpeed(foliageData.speed);
    }
    
    this.terrain = this.scene.getTerrain();
    this.terrain.setTileset(tilesetName);
    
    this.terrain.setFoliageSet(foliageName);
    
    byte[] bytes                        = Base64Coder.decode(terrainData);
    inflater.setInput(bytes);

    
    byte[] buffer = new byte[1024];
    ByteArrayOutputStream stream = new ByteArrayOutputStream(bytes.length);
    
    try {
      while(!inflater.finished()){ 
        int count = inflater.inflate(buffer);
        stream.write(buffer, 0, count);
      }
      stream.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    ByteArrayInputStream is = new ByteArrayInputStream(stream.toByteArray());
    DataInputStream dis     = new DataInputStream(is);
    terrainData = null;
    
    try {
      for (int z = 0; z < rows; z++) {
        for (int x = 0; x < columns; x++) {
          Tile tile = new Tile(x, 0, z);
          tile.setGid(dis.readInt());
          tile.setY(dis.readFloat());
          tile.setY1(dis.readFloat());
          tile.setY2(dis.readFloat());
          tile.setY3(dis.readFloat());
          tile.setY4(dis.readFloat());
          
          String aid = dis.readUTF();
          int ord = dis.readInt();
          
          tile.setPassable(dis.readBoolean());
          tile.setLiquid(dis.readBoolean());
          tile.setLiquidHeight(dis.readFloat());
          String foliageRegionName = dis.readUTF();
          
          AutoTiles autoTiles  = terrain.getTileset().getAutoTiles(aid);
          AutoTiles.Types type = AutoTiles.Types.values()[ord];
          
          tile.setAutoTile(autoTiles.getAutoTile(type));
          if (foliageRegionName.length() > 0) {
            tile.setFoliage(this.terrain.getFoliageSet().findDescriptor(foliageRegionName));
          }
          terrain.setTile(x, z, tile);
        }
      }
      dis.close();
      is.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    this.terrain.fillEmptyTilesWithDebugTile();
  }
  
  @Persist
  public void prepare() {
    Water water      = scene.getWater();
    Foliage foliage  = scene.getFoliage();
    this.foliageData = new FoliagePersister();
    
    foliageData.amplitude = foliage.getAmplitude();
    foliageData.speed     = foliage.getSpeed();
    
    this.waterData   = new WaterPersister();
    
    waterData.alpha          = water.getAlpha();
    waterData.mix            = water.getMix();
    waterData.amplitude      = water.getAmplitudeWave();
    waterData.animationSpeed = water.getWaterAnimationSpeed();
    waterData.speed          = water.getAngleWaveSpeed();
    waterData.material       = water.getWaterMaterial();
    
    
    Deflater deflater = new Deflater();
    deflater.setLevel(9);
    deflater.setStrategy(Deflater.BEST_SPEED);
    
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    DataOutputStream dos                   = new DataOutputStream(byteOutputStream);
    
    try {
      for (int z = 0; z < rows; z++) {
        for (int x = 0; x < columns; x++) {
          Tile tile = terrain.getTile(x, z);
          dos.writeInt(tile.getGid());
          dos.writeFloat(tile.getY());
          dos.writeFloat(tile.getY1());
          dos.writeFloat(tile.getY2());
          dos.writeFloat(tile.getY3());
          dos.writeFloat(tile.getY4());
          dos.writeUTF(tile.getAutoTile().getAutoTiles().getName());
          dos.writeInt(tile.getAutoType().ordinal());
          dos.writeBoolean(tile.isPassable());
          dos.writeBoolean(tile.isLiquid());
          dos.writeFloat(tile.getLiquidHeight());
          if (tile.haveFoliage()) {
            dos.writeUTF(tile.getFoliage().getRegionName());
          } else {
            dos.writeUTF("");
          }
        }
      }
      
      this.uncompressedSize = dos.size();
      dos.close();
      byteOutputStream.close();
      
      byte[] bytes = byteOutputStream.toByteArray();
      deflater.setInput(bytes);
      deflater.finish();
      
      byteOutputStream = new ByteArrayOutputStream(bytes.length);
      byte[] buffer    = new byte[1024];
      
      while(!deflater.finished()){ 
        int bytesCompressed = deflater.deflate(buffer);
        byteOutputStream.write(buffer,0,bytesCompressed);
      }
      
      this.compressedSize = byteOutputStream.size();
      byteOutputStream.close();
      
      terrainData = new String(Base64Coder.encode(byteOutputStream.toByteArray()));
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Complete
  public void release() {
    terrainData = null;
  }

  public Scene getScene() {
    return scene;
  }

  public boolean isSkipLoadingTerrainData() {
    return skipLoadingTerrainData;
  }

  public void setSkipLoadingTerrainData(boolean skipLoadingTerrainData) {
    this.skipLoadingTerrainData = skipLoadingTerrainData;
  }

  public String getUID() {
    return this.uid;
  }

  public int getUncompressedSize() {
    return uncompressedSize;
  }

  public int getCompressedSize() {
    return compressedSize;
  }

  public void setUncompressedSize(int uncompressedSize) {
    this.uncompressedSize = uncompressedSize;
  }

  public void setCompressedSize(int compressedSize) {
    this.compressedSize = compressedSize;
  }
}
