grammar PomdpDsl;

pomdp:
    DISCOUNT COLON discount=NUMBER NEWLINE
    VALUES COLON values=value_tail NEWLINE
    ;
    
    value_tail : REWARD|COST;

    NUMBER : [1-9][0-9]*;
    DISCOUNT : 'DISCOUNT';
    VALUES : 'VALUES';
    REWARD : 'REWARD';
    COST : 'COST';
    COLON : ':';