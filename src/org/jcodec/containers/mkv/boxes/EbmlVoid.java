package org.jcodec.containers.mkv.boxes;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jcodec.common.io.SeekableByteChannel;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class EbmlVoid extends EbmlBase {

    public EbmlVoid(byte[] id) {
        this.id = id;
    }

    @Override
    public ByteBuffer getData() {
        return null;
    }
    
    public void skip(SeekableByteChannel is) throws IOException{
        is.position(this.dataOffset+this.dataLen);
    }

}
