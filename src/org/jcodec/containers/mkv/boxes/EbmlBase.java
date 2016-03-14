package org.jcodec.containers.mkv.boxes;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mkv.MKVType;
import org.jcodec.containers.mkv.util.EbmlUtil;
import org.jcodec.platform.Platform;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed under FreeBSD License
 * 
 * EBML IO implementation
 * 
 * @author The JCodec project
 * 
 */
public abstract class EbmlBase {

    protected EbmlMaster parent;
    public MKVType type;
    public byte[] id = { 0x00 };
    public int dataLen = 0;
    public long offset;
    public long dataOffset;
    public int typeSizeLength;

    public boolean equalId(byte[] typeId) {
        return Platform.arrayEquals(this.id, typeId);
    }
    
    public abstract ByteBuffer getData();
    
    public long size() {
        return this.dataLen + EbmlUtil.ebmlLength(dataLen) + id.length;
    }

    public long mux(SeekableByteChannel os) throws IOException {
        ByteBuffer bb = getData();
        return os.write(bb);
    }
    

}
