package org.infobip.mobile.messaging;

import org.infobip.mobile.messaging.util.DateTimeUtil;

import java.security.InvalidParameterException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * This class wraps custom attribute types used to interact with backend services. The custom parameters may be of following types:
 * <ul>
 * <li>{@link String}</li>
 * <li>{@link Number}</li>
 * <li>{@link Date}</li>
 * <li>{@link Boolean}</li>
 * </ul>
 *
 * @see User#setCustomAttributes(Map)
 * @see User#setCustomAttribute(String, CustomAttributeValue)
 * @see User#getCustomAttributes()
 * @see User#getCustomAttributeValue(String)
 * @see Installation#setCustomAttributes(Map)
 * @see Installation#setCustomAttribute(String, CustomAttributeValue)
 * @see Installation#getCustomAttributes()
 * @see Installation#getCustomAttributeValue(String)
 */
public class CustomAttributeValue {

    public enum Type {
        String,
        Number,
        Date,
        Boolean
    }

    private Object value;
    private final Type type;

    public CustomAttributeValue(String someString) {
        this.value = someString;
        this.type = Type.String;
    }

    public CustomAttributeValue(Number someNumber) {
        this.value = someNumber;
        this.type = Type.Number;
    }

    public CustomAttributeValue(Date someDate) {
        this.value = DateTimeUtil.DateToYMDString(someDate);
        this.type = Type.Date;
    }

    public CustomAttributeValue(Boolean someBoolean) {
        this.value = someBoolean;
        this.type = Type.Boolean;
    }

    /**
     * Parses string into CustomAttributeValue based on desired format.
     * <br>
     * For Date type this constructor accepts "yyyy-MM-dd" representation of date (for example 2016-12-31).
     *
     * @throws ParseException            if stringValue cannot be parsed to {@code CustomAttributeValue}
     * @throws InvalidParameterException if provided type is invalid
     */
    public CustomAttributeValue(String stringValue, Type type) throws ParseException, InvalidParameterException {
        this.type = type;
        switch (type) {
            case String:
                this.value = stringValue;
                break;
            case Number:
                this.value = NumberFormat.getNumberInstance(Locale.getDefault()).parse(stringValue);
                break;
            case Date:
                DateTimeUtil.DateFromYMDString(stringValue);
                this.value = stringValue;
                break;
            case Boolean:
                this.value = Boolean.valueOf(stringValue);
                break;
            default:
                throw new InvalidParameterException();
        }
    }

    protected CustomAttributeValue(CustomAttributeValue that) {
        this.value = that.value;
        this.type = that.type;
    }

    /**
     * Return the value of specified {@code CustomAttributeValue} as {@link String}.
     *
     * @return {@link String}
     * @throws ClassCastException if {@code CustomAttributeValue} is not of {@link String} type.
     */
    public String stringValue() {
        if (!(value instanceof String) || type != Type.String) {
            throw new ClassCastException();
        }

        return (String) value;
    }

    /**
     * Return the value of specified {@code CustomAttributeValue} as {@link Number}.
     *
     * @return {@link Number}
     * @throws ClassCastException if {@code CustomAttributeValue} is not of {@link Number} type.
     */
    public Number numberValue() {
        if (!(value instanceof Number) || type != Type.Number) {
            throw new ClassCastException();
        }

        return (Number) value;
    }

    /**
     * Return the value of specified {@code CustomAttributeValue} as {@link Date}.
     *
     * @return {@link Date}
     * @throws ClassCastException if {@code CustomAttributeValue} is not of {@link Date} type.
     */
    public Date dateValue() {
        if (!(value instanceof String) || type != Type.Date) {
            throw new ClassCastException();
        }

        try {
            return DateTimeUtil.DateFromYMDString((String) value);
        } catch (ParseException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    /**
     * Return the value of specified {@code CustomAttributeValue} as {@link Boolean}.
     *
     * @return {@link Boolean}
     * @throws ClassCastException if {@code CustomAttributeValue} is not of {@link Boolean} type.
     */
    public Boolean booleanValue() {
        if (!(value instanceof Boolean) || type != Type.Boolean) {
            throw new ClassCastException();
        }

        return Boolean.valueOf("" + value);
    }

    public Type getType() {
        return type;
    }

    protected Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (this.type == null) {
            return super.toString();
        }

        switch (type) {
            case String:
                return stringValue();
            case Date:
                return DateTimeUtil.DateToYMDString(dateValue());
            case Number:
                return "" + numberValue();
            case Boolean:
                return "" + booleanValue();
            default:
                return super.toString();
        }
    }
}