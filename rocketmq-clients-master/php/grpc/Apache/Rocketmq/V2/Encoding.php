<?php
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: apache/rocketmq/v2/definition.proto

namespace Apache\Rocketmq\V2;

use UnexpectedValueException;

/**
 * Protobuf type <code>apache.rocketmq.v2.Encoding</code>
 */
class Encoding
{
    /**
     * Generated from protobuf enum <code>ENCODING_UNSPECIFIED = 0;</code>
     */
    const ENCODING_UNSPECIFIED = 0;
    /**
     * Generated from protobuf enum <code>IDENTITY = 1;</code>
     */
    const IDENTITY = 1;
    /**
     * Generated from protobuf enum <code>GZIP = 2;</code>
     */
    const GZIP = 2;

    private static $valueToName = [
        self::ENCODING_UNSPECIFIED => 'ENCODING_UNSPECIFIED',
        self::IDENTITY => 'IDENTITY',
        self::GZIP => 'GZIP',
    ];

    public static function name($value)
    {
        if (!isset(self::$valueToName[$value])) {
            throw new UnexpectedValueException(sprintf(
                    'Enum %s has no name defined for value %s', __CLASS__, $value));
        }
        return self::$valueToName[$value];
    }


    public static function value($name)
    {
        $const = __CLASS__ . '::' . strtoupper($name);
        if (!defined($const)) {
            throw new UnexpectedValueException(sprintf(
                    'Enum %s has no value defined for name %s', __CLASS__, $name));
        }
        return constant($const);
    }
}

