grammar PomdpDsl;

pomdp:
    DISCOUNT discount=PROB
    VALUES values=value_tail
    STATES (numberOfStates=NUMBER|(states+=STATE)+)
    ;

value_tail : REWARD|COST;

COLON : ':';
DISCOUNT : 'discount' COLON WS ;
VALUES : 'values' COLON WS ;
REWARD : 'reward';
COST : 'cost';
STATES : 'states' COLON WS ;

PROB : '0.''0'*| '1.''0'*| '0.'[0-9]*;
NUMBER : [1-9][0-9]* ;
STRING : ALPHANUMERIC ;
ALPHA : [a-zA-Z]+ ;
NEWLINE : [\n|\r] -> skip ;
WS : [ |\t]+ -> skip ;
COMMENT : '#' ~( '\r' | '\n' )* -> skip;
fragment ALPHANUMERIC : ALPHA (ALLOWEDATTCHAR)* ;
fragment ALLOWEDATTCHAR : '-' | '_'| [0-9] | ALPHA ;

STATE : ID ;
ACTION : ID ;
ID : '0' | NUMBER | STRING ;