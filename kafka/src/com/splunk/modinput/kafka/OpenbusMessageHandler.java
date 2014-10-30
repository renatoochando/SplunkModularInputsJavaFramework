package com.splunk.modinput.kafka;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.log4j.Logger;

import br.com.produban.openbus.camus.schemaregistry.AvroLocalSchemaRegistry;

import com.linkedin.camus.schemaregistry.SchemaRegistry;
import com.splunk.modinput.SplunkLogEvent;
import com.splunk.modinput.Stream;
import com.splunk.modinput.StreamEvent;
import com.splunk.modinput.kafka.KafkaModularInput.MessageReceiver;

public class OpenbusMessageHandler extends AbstractMessageHandler {

	private SchemaRegistry<Schema> schemaRegistry = new AvroLocalSchemaRegistry();

	private DecoderFactory decoderFactory = DecoderFactory.get();

	private static Logger logger = Logger.getLogger(OpenbusMessageHandler.class);

	private static final byte MAGIC_BYTE = 0x0;

	@Override
	public Stream handleMessage(byte[] messageContents, MessageReceiver context) throws Exception {

		// check & discard MAGIC_BYTE
		ByteBuffer buffer = getByteBuffer(messageContents);
		if (buffer == null) {
			return null;
		}

		// read ID
		int id = buffer.getInt();

		Schema schema = schemaRegistry.getSchemaByID(null, Integer.toString(id));
		
		Stream stream = new Stream();
		ArrayList<StreamEvent> list = new ArrayList<StreamEvent>();

		try {
			DatumReader<Record> reader = new GenericDatumReader<Record>(schema);
			Decoder decoder = decoderFactory.binaryDecoder(buffer.array(), buffer.position(), buffer.remaining(), null);
			final Record record = reader.read(null, decoder);

			logger.debug("Record: " + record.toString());
			logger.debug("host: " + record.get("host"));
			logger.debug("key: " + record.get("key"));
			logger.debug("value: " + record.get("value"));
			logger.debug("clock: " + record.get("clock"));
			logger.debug("ns: " + record.get("ns"));

			SplunkLogEvent splunkEvent = buildCommonEventMessagePart(context);
			splunkEvent.addPair("msg_body", record.get("value"));
			
			StreamEvent event = new StreamEvent();
			event.setUnbroken("1");
			event.setData(record.toString());
			event.setStanza(context.stanzaName);

			list.add(event);
			
		} catch (Exception e) {
			logger.error("Error handling message" + Arrays.toString(messageContents), e);
		}
		
		stream.setEvents(list);

		return stream;
	}

	private ByteBuffer getByteBuffer(byte[] payload) {
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		if (buffer.get() != MAGIC_BYTE) {
			logger.error("Unknown magic byte!");
			return null;
		}
		return buffer;
	}

	/**
	 * Properties example:
	 * camus.message.decoder.class=br.com.produban.openbus.camus.coders.AvroMessageDecoder
	 * kafka.message.coder.schema.registry.class=br.com.produban.openbus.camus.schemaregistry.AvroLocalSchemaRegistry
	 * kafka.registry.schemaPackage=br.com.produban.openbus.model.avro
	 */
	@Override
	public void setParams(Map<String, String> params) {
		Properties properties = new Properties();
		for (Entry<String, String> entry : params.entrySet())
			properties.setProperty(entry.getKey(), entry.getValue());

		schemaRegistry.init(properties);
	}

}
