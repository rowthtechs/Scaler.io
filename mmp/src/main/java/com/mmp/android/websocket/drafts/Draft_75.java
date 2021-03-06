package com.mmp.android.websocket.drafts;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.mmp.android.websocket.exceptions.InvalidDataException;
import com.mmp.android.websocket.exceptions.InvalidFrameException;
import com.mmp.android.websocket.exceptions.InvalidHandshakeException;
import com.mmp.android.websocket.exceptions.LimitExedeedException;
import com.mmp.android.websocket.exceptions.NotSendableException;
import com.mmp.android.websocket.framing.CloseFrame;
import com.mmp.android.websocket.framing.FrameBuilder;
import com.mmp.android.websocket.framing.Framedata;
import com.mmp.android.websocket.framing.Framedata.Opcode;
import com.mmp.android.websocket.framing.FramedataImpl1;
import com.mmp.android.websocket.handshake.ClientHandshake;
import com.mmp.android.websocket.handshake.ClientHandshakeBuilder;
import com.mmp.android.websocket.handshake.HandshakeBuilder;
import com.mmp.android.websocket.handshake.ServerHandshake;
import com.mmp.android.websocket.handshake.ServerHandshakeBuilder;
import com.mmp.android.websocket.util.Charsetfunctions;

public class Draft_75 extends Draft {

	/**
	 * The byte representing CR, or Carriage Return, or \r
	 */
	public static final byte CR = (byte) 0x0D;
	/**
	 * The byte representing LF, or Line Feed, or \n
	 */
	public static final byte LF = (byte) 0x0A;
	/**
	 * The byte representing the beginning of a WebSocket text frame.
	 */
	public static final byte START_OF_FRAME = (byte) 0x00;
	/**
	 * The byte representing the end of a WebSocket text frame.
	 */
	public static final byte END_OF_FRAME = (byte) 0xFF;

	/** Is only used to detect protocol violations */
	protected boolean readingState = false;

	protected List<Framedata> readyframes = new LinkedList<Framedata>();
	protected ByteBuffer currentFrame;

	private final SecureRandom reuseableRandom = new SecureRandom();

	@Override
	public HandshakeState acceptHandshakeAsClient( ClientHandshake request, ServerHandshake response ) {
		return request.getFieldValue( "WebSocket-Origin" ).equals( response.getFieldValue( "Origin" ) ) && basicAccept( response ) ? HandshakeState.MATCHED : HandshakeState.NOT_MATCHED;
	}

	@Override
	public HandshakeState acceptHandshakeAsServer( ClientHandshake handshakedata ) {
		if( handshakedata.hasFieldValue( "Origin" ) && basicAccept( handshakedata ) ) {
			return HandshakeState.MATCHED;
		}
		return HandshakeState.NOT_MATCHED;
	}

	@Override
	public ByteBuffer createBinaryFrame( Framedata framedata ) {
		if( framedata.getOpcode() != Opcode.TEXT ) {
			throw new RuntimeException( "only text frames supported" );
		}

		ByteBuffer pay = framedata.getPayloadData();
		ByteBuffer b = ByteBuffer.allocate( pay.remaining() + 2 );
		b.put( START_OF_FRAME );
		pay.mark();
		b.put( pay );
		pay.reset();
		b.put( END_OF_FRAME );
		b.flip();
		return b;
	}

	@Override
	public List<Framedata> createFrames( ByteBuffer binary, boolean mask ) {
		throw new RuntimeException( "not yet implemented" );
	}

	@Override
	public List<Framedata> createFrames( String text, boolean mask ) {
		FrameBuilder frame = new FramedataImpl1();
		try {
			frame.setPayload( ByteBuffer.wrap( Charsetfunctions.utf8Bytes( text ) ) );
		} catch ( InvalidDataException e ) {
			throw new NotSendableException( e );
		}
		frame.setFin( true );
		frame.setOptcode( Opcode.TEXT );
		frame.setTransferemasked( mask );
		return Collections.singletonList( (Framedata) frame );
	}

	@Override
	public ClientHandshakeBuilder postProcessHandshakeRequestAsClient( ClientHandshakeBuilder request ) throws InvalidHandshakeException {
		request.put( "Upgrade", "WebSocket" );
		request.put( "Connection", "Upgrade" );
		if( !request.hasFieldValue( "Origin" ) ) {
			request.put( "Origin", "random" + reuseableRandom.nextInt() );
		}

		return request;
	}

	@Override
	public HandshakeBuilder postProcessHandshakeResponseAsServer( ClientHandshake request, ServerHandshakeBuilder response ) throws InvalidHandshakeException {
		response.setHttpStatusMessage( "Web Socket Protocol Handshake" );
		response.put( "Upgrade", "WebSocket" );
		response.put( "Connection", request.getFieldValue( "Connection" ) ); // to respond to a Connection keep alive
		response.put( "WebSocket-Origin", request.getFieldValue( "Origin" ) );
		String location = "ws://" + request.getFieldValue( "Host" ) + request.getResourceDescriptor();
		response.put( "WebSocket-Location", location );
		// TODO handle Sec-WebSocket-Protocol and Set-Cookie
		return response;
	}

	protected List<Framedata> translateRegularFrame( ByteBuffer buffer ) throws InvalidDataException {

		while ( buffer.hasRemaining() ) {
			byte newestByte = buffer.get();
			if( newestByte == START_OF_FRAME ) { // Beginning of Frame
				if( readingState )
					throw new InvalidFrameException( "unexpected START_OF_FRAME" );
				readingState = true;
			} else if( newestByte == END_OF_FRAME ) { // End of Frame
				if( !readingState )
					throw new InvalidFrameException( "unexpected END_OF_FRAME" );
				// currentFrame will be null if END_OF_FRAME was send directly after
				// START_OF_FRAME, thus we will send 'null' as the sent message.
				if( this.currentFrame != null ) {
					currentFrame.flip();
					FramedataImpl1 curframe = new FramedataImpl1();
					curframe.setPayload( currentFrame );
					curframe.setFin( true );
					curframe.setOptcode( Opcode.TEXT );
					readyframes.add( curframe );
					this.currentFrame = null;
					buffer.mark();
				}
				readingState = false;
			} else if( readingState ) { // Regular frame data, add to current frame buffer //TODO This code is very expensive and slow
				if( currentFrame == null ) {
					currentFrame = createBuffer();
				} else if( !currentFrame.hasRemaining() ) {
					currentFrame = increaseBuffer( currentFrame );
				}
				currentFrame.put( newestByte );
			} else {
				return null;
			}
		}

		// if no error occurred this block will be reached
		/*if( readingState ) {
			checkAlloc(currentFrame.position()+1);
		}*/

		List<Framedata> frames = readyframes;
		readyframes = new LinkedList<Framedata>();
		return frames;
	}

	@Override
	public List<Framedata> translateFrame( ByteBuffer buffer ) throws InvalidDataException {
		List<Framedata> frames = translateRegularFrame( buffer );
		if( frames == null ) {
			throw new InvalidDataException( CloseFrame.PROTOCOL_ERROR );
		}
		return frames;
	}

	@Override
	public void reset() {
		readingState = false;
		this.currentFrame = null;
	}

	@Override
	public CloseHandshakeType getCloseHandshakeType() {
		return CloseHandshakeType.NONE;
	}

	public ByteBuffer createBuffer() {
		return ByteBuffer.allocate( INITIAL_FAMESIZE );
	}

	public ByteBuffer increaseBuffer( ByteBuffer full ) throws LimitExedeedException , InvalidDataException {
		full.flip();
		ByteBuffer newbuffer = ByteBuffer.allocate( checkAlloc( full.capacity() * 2 ) );
		newbuffer.put( full );
		return newbuffer;
	}

	@Override
	public Draft copyInstance() {
		return new Draft_75();
	}
}
