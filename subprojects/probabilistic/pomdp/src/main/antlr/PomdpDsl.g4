grammar PomdpDsl;

pomdp:
    DISCOUNT discount=PROB
    VALUES values=Value_tail
    STATES ((numberOfStates=NUMBER) | ((states+=STRING)+))
    ACTIONS ((numberOfActions=NUMBER) | ((actions+=id)+))
    OBSERVATIONS ((numberOfObservations=NUMBER) | ((observations+=id)+))
    START (beliefStateProbs+=PROB)+
    (transitions+=transition)+
;

Value_tail : REWARD|COST;
sourceWithProbs : (probs+=PROB)+ ';';
transition :
    (T action=id COLON source=id COLON destination=id prob=PROB)
    | (T action=id COLON source=id (probs+=PROB)+)
    | (T action=id (sources+=sourceWithProbs)+)
;

PROB : ('1.''0'+) | ('0.'[0-9]+);

COLON : ':';
DISCOUNT : 'discount' COLON;
VALUES : 'values' COLON;
REWARD : 'reward';
COST : 'cost';
STATES : 'states' COLON  ;
ACTIONS : 'actions' COLON  ;
OBSERVATIONS : 'observations' COLON  ;
START : 'start' COLON ;
T : 'T' COLON  ;
OBSERVATION : 'O' COLON  ;
REWARDS : 'R' COLON  ;
JOKER : '*';

NUMBER : [1-9][0-9]* ;
ZERO: '0';
STRING : ALPHANUMERIC ;
ALPHA : [a-zA-Z];
fragment ALPHANUMERIC : ALPHA (ALLOWEDATTCHAR)* ;
fragment ALLOWEDATTCHAR : '-' | '_'| [0-9] | ALPHA ;

id : ZERO | NUMBER | STRING ;
NEWLINE : [\n\r]+ -> skip;
WS : [\t]+ -> skip;
SPACE : ' ' -> skip;
COMMENT : '#' ~( '\r' | '\n' )* -> skip;