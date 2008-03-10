
#define CCN_TT_BITS 3
#define CCN_TT_MASK ((1 << CCN_TT_BITS) - 1)
#define CCN_MAX_TINY ((1 << (7-CCN_TT_BITS)) - 1)

enum ccn_tt {
    CCN_BUILTIN,    /* predefined builtin encoding - numval is vocab index */
    CCN_TAG,        /* starts composite - numval is tagnamelen-1 */ 
    CCN_ATTR,       /* starts attribute - numval is attrnamelen-1 */
    CCN_INTVAL,     /* non-negative integer of any magnitude - numval is value */
    CCN_BLOB,       /* opaque binary data - numval is byte count */
    CCN_UDATA,      /* UTF-8 encoded character data - numval is byte count */
};

#define CCN_CLOSE ((unsigned char)(1 << 7))

enum ccn_vocab {
    CCN_UNKNOWN_BUILTIN = -2,
    CCN_NO_SCHEMA = -1,
    CCN_PROCESSING_INSTRUCTIONS = 16, /* <?name:U value:U?> */
    
};

struct ccn_schema;
struct ccn_schema_data {
    enum ccn_tt tt;
    const unsigned char *ident; /* UTF-8 nul-terminated */
    enum ccn_vocab code;
    struct ccn_schema *schema;
};

enum ccn_schema_type {
    CCN_SCHEMA_LABEL,
    CCN_SCHEMA_NONTERMINAL,
    CCN_SCHEMA_ALT,
    CCN_SCHEMA_SEQ,
};

struct ccn_schema {
    enum ccn_schema_type schema_type;
    struct ccn_schema_data *data;
    struct ccn_schema *left;
    struct ccn_schema *right;
};
