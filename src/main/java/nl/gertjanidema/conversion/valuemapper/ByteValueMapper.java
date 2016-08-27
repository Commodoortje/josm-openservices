package nl.gertjanidema.conversion.valuemapper;

import java.text.ParseException;

public class ByteValueMapper extends NumberValueMapper<Byte> {

    @Override
    public Byte parse(String source) throws ValueMapperException {
        if (isNull(source)) {
            return null;
        }
        try {
            return format.parse(source).byteValue();
        } catch (ParseException e) {
            throw new ValueMapperException("Invalid number format", e);
        }
    }

    @Override
    protected String typedFormat(Byte object) throws ValueMapperException {
        if (object == null) {
            return super.typedFormat(object);
        }
        return format.format(object);
    }

    @Override
    public Class<Byte> getType() {
        return Byte.class;
    }
}
