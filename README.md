# mod-data-loader

Copyright (C) 2017-2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

RMB-based module used to load marc data and convert it to instance records.

## APIs
1. POST `/load/marc-rules` - uploads a [rules json](https://github.com/folio-org/test-data-loader/blob/master/ramls/rules.json) file to use when mapping marc fields to instance fields. The rules file is only stored in memory and will be associated with the tenant passed in the x-okapi-tenant header
2. POST `/load/marc-data/test` - normalizes the attached binary MARC file (should be small) and returns json instance object as the response to the API. Can be used to check mappings from MARC to instances. The file attached should be kept small so that there are no memory issues for the client (up to 500 entries)

### Some notes

 1. A tenant must be passed in the x-okapi-tenant header.
 2. A rules files must be set for that tenant.

### Conversion rules

Control fields can be used to insert constant values into instance fields. For example, the below will insert the value Books into the instanceTypeId field if all conditions of this rule are met. Multiple rules may be declared. The `LDR` field indicates that the condition should be tested against the MARC's Leader field data.

```
 "rules": [
   {
     "conditions": [
       {
         "type": "char_select",
         "parameter": "0",
         "value": "7"
       },
       {
         "type": "char_select",
         "parameter": "1",
         "value": "8"
       },
       {
         "type": "char_select",
         "parameter": "0",
         "value": "0",
         "LDR": true
       }
     ],
     "value": "Books"
   }
 ]
```

#### Available functions

 - `char_select` - select a specific char (parameter) from the field and compare it to the indicated value (value) - ranges can be passed in as well (e.g. 1-3). `LDR` indicates that the data from the leader field should be used for this condition and not the data of the field itself
 - `remove_ending_punc` remove punctuation at the end of the data field (;:,/+=<space> as well as period handling .,..,...,....)
 - `trim_period` if the last char in the field is a period it is removed
 - `trim` remove leading and trailing spaces from the data field

Example:
```
 "rules": [
   {
     "conditions": [
       {
         "type": "remove_ending_punc,trim"
       }
     ]
   }
 ]
```
Note that you can indicate the use of multiple functions by using the comma delimiter. This is only possible for functions that do not receive parameters.
- `custom` - define a custom JavaScript function to run on the field's data (passed in as DATA to the JavaScript function as a bound variable. Must return a String value). For example:

```
"target": "publication.dateOfPublication",
"rules": [
  {
    "conditions": [
      {
        "type": "custom",
        "value": "DATA.replace(/\\D/g,'');"
      }
    ]
  }
]
```

#### Mapping partial data

To set an instance field with part of the data appearing in a specific subfield,
```
"rules": [
  {
    "conditions": [
      {
        "type": "char_select",
        "parameter": "35-37"
      }
    ]
  }
]
```
Notice that there is no `value` in the condition and no constant value set at the `rule` level.

#### Multiple subfields

Indicating multiple subfields will concatenate the values of each subfield into the target instance field:
```
"690": [
    {
      "subfield": ["a","y","5"],
      "description": "local subjects",
      "target": "subjects"
    }
  ]
```

#### Grouping fields into an object

Normally, all mappings in a single field that refer to the same object type will be mapped to a single object. For example, the below will map two subfields found in 001 to two different fields in the same identifier object within the instance.

```
  "001": [
    {
      "target": "identifiers.identifierTypeId",
      "description": "Type for Control Number (001)",
      ....
    },
    {
      "target": "identifiers.value",
      "description": "Control Number (001)",
  ...
    }
  ],
```
However, sometimes there is a need to create multiple objects (for example, multiple identifier objects) from subfields within a single field.
Consider the following MARC field:

`020    ##$a0877790019$qblack leather$z0877780116 :$c$14.00`

which should map to:
```
"identifiers": [
  { "value": "0877790019", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"},
  { "value": "0877780116", "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"}
]
```

To achieve this, you can wrap multiple subfield definitions into an `entity` field. In the below, both subfields will be mapped to the same object (which would happen normally), however, any additional entries outside of the `"entity"` definitions will be mapped to a new object, hence allowing you to create multiple objects from a single MARC field.
```
"020": [
    {
      "entity": [
        {
        "target": "identifiers.identifierTypeId",
        "description": "Type for Control Number (020)",
        "subfield": ["a"],
        ...
        },
        {
          "target": "identifiers.value",
          "description": "Control Number (020)",
          "subfield": ["b"],
          ...
        }
      ]
    },
```

##### Handling repeating fields

The `entity` example will concatenate together values from repeated fields. For example, an `entity` on subfield  "a" will concatenate all values in all the "a" subfields (if they repeat) - and map the concatenated value to the declared field. If there is a need to have each "a" subfield generate its own object within the instance (for example, each "a" subfield should create a separate classification entry and should not be concatenated within a single entry). The following field can be added to the configuration: `"entityPerRepeatedSubfield": true`

```
 "050": [
    {
      "entityPerRepeatedSubfield": true,
      "entity": [
        {
          "target": "classifications.classificationTypeId",
          "subfield": ["a"],
          "rules": [
            {
              "conditions": [],
              "value": "99999999-be78-422d-bd51-4ed9f33c3422"
            }
          ]
        },
        {
          "target": "classifications.classificationNumber",
          "subfield": ["a"]
        }
      ]
    },
    {
      "entityPerRepeatedSubfield": true,
      "entity": [
        {
          "target": "classifications.classificationTypeId",
          "subfield": ["b"],
          "rules": [
            {
              "conditions": [],
              "value": "99999999-be78-422d-bd51-4ed9f33c3423"
            }
          ]
        },
        {
          "target": "classifications.classificationNumber",
          "subfield": ["b"]
        }
      ]
    }
  ],

```

#### Delimiting subfields

As previously mentioned, grouping subfields  `"subfield": [ "a", "y", "5" ]` will concatenate (space delimited) the values in those subfields and place the result in the target. However, if there is a need to declare different delimiters per set of subfields, the following can be declared using the `"subFieldDelimiter"` array:

```
  "600": [
    {
      "subfield": [
        "a","b","c","d","v","x","y","z"
      ],
      "description": "",
      "subFieldDelimiter": [
        {
          "value": "--",
          "subfields": [
            "d","v","x","y","z"
          ]
        },
        {
          "value": " ",
          "subfields": ["a", "b", "c"]
        },
        {
          "value": "&&&",
          "subfields": []
        }
      ],
      "target": "subjects"
    }
  ]
```
An empty subfields array indicates that this will be used to separate values from different subfield sets (subfields associated with a specific separator).

#### A single subfield into multiple subfields

It is sometimes necessary to parse data in a single subfield and map the output into multiple subfields before processing.
For example:
`041 $aitaspa`
We may want to take this language field and convert it into two $a subfields before we begin processing. This can be achieved in the following manner:

```
"041": [
  {
    "entityPerRepeatedSubfield": true,
    "entity": [
      {
        "subfield": ["a"],
        "subFieldSplit": {
          "type": "custom",
          "value": "DATA.match(/.{1,3}/g)"
        },
        "rules": [
          {
            "conditions": [
              {
                "type": "trim"
              }
            ]
          }
        ],
        "description": "",
        "target": "languages"
      }
...
```

Once pre-processing is complete, the regular rules / mappings will be applied - this includes the entity option which can map each of the newly created subfields into separate objects.

There are currently 2 functions that can be called to parse the data within a single subfield:

 **`split_every`** which receives a value indicating a hard split every n characters
```
"subFieldSplit": {
   "type": "split_every",
   "value": "3"
 },
```

**`custom`** - which receives a JavasSript function and must return a string array representing the new values generated from the original data.
```
"subFieldSplit": {
  "type": "custom",
  "value": "DATA.match(/.{1,3}/g)"
}
```

#### Processing rules on concatenated data

By default rules run on the data in a single subfield, hence, concatenated subfields concatenate normalized data. In order to concatenate
un-normalized data, and run the rules on the concatenated data add the following field: `"applyRulesOnConcatedData": true,`
This can be used when punctuation should only be removed from the end of a concatenated string.
```
"500": [
    {
      "subfield": [
        "a"
      ],
      "applyRulesOnConcatenatedData": true,
      "description": "",
```

#### JSON fields supported only on data field (not control fields)

1. `subFieldSplit`
2. `subFieldDelimiter`
3. `applyRulesOnConcatenatedData`
4. `entity`
5. `entityPerRepeatedSubfield`
