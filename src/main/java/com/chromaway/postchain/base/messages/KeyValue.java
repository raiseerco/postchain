/*
 * Generated by ASN.1 Java Compiler (http://www.asnlab.org/)
 * From ASN.1 module "Messages"
 */
package com.chromaway.postchain.base.messages;

import java.io.*;
import java.math.*;
import org.asnlab.asndt.runtime.conv.*;
import org.asnlab.asndt.runtime.conv.annotation.*;
import org.asnlab.asndt.runtime.type.AsnType;
import org.asnlab.asndt.runtime.value.*;

public class KeyValue {

	@Component(0)
	public Long key;

	@Component(1)
	public byte[] value;


	public boolean equals(Object obj) {
		if(!(obj instanceof KeyValue)){
			return false;
		}
		return TYPE.equals(this, obj, CONV);
	}

	public void der_encode(OutputStream out) throws IOException {
		TYPE.encode(this, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV, out);
	}

	public static KeyValue der_decode(InputStream in) throws IOException {
		return (KeyValue)TYPE.decode(in, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV);
	}


	public final static AsnType TYPE = Messages.type(65537);

	public final static CompositeConverter CONV;

	static {
		CONV = new AnnotationCompositeConverter(KeyValue.class);
		AsnConverter keyConverter = LongConverter.INSTANCE;
		AsnConverter valueConverter = OctetStringConverter.INSTANCE;
		CONV.setComponentConverters(new AsnConverter[] { keyConverter, valueConverter });
	}


}
