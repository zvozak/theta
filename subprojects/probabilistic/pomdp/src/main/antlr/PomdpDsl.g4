grammar PomdpDsl;

pomdp:
    DISCOUNT discount=PROB
    VALUES values=Value_tail
    STATES ((numberOfStates=NUMBER) | ((states+=STRING)+))
    ACTIONS ((numberOfActions=NUMBER) | ((actions+=id)+))
    OBSERVATIONS ((numberOfObservations=NUMBER) | ((observations+=id)+))
    (START (beliefStateProbs+=PROB)+)?
    (transitions+=transition)+
    (observationfunction+=observation)+
;

Value_tail : REWARD|COST;
sourceWithProbs : (probs+=PROB)+ ';';
transition :
    (T action=id COLON source=id COLON destination=id prob=PROB)
    | (T action=id COLON source=id (probs+=PROB)+)
    | (T action=id (sources+=sourceWithProbs)+)
;
destinationWithProbs : (probs+=PROB)+ ';';
observation :
    (O action=id COLON destination=id (probs+=PROB)+)
    | (O action=id (destinations+=destinationWithProbs)+)
;

PROB : ZERO | ONE | ( ONE'.' ZERO+) | (ZERO '.'[0-9]+);

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
O : 'O' COLON  ;
REWARDS : 'R' COLON  ;
JOKER : '*';

NUMBER : [1-9][0-9]* ;
ZERO: '0';
ONE: '1';
STRING : ALPHANUMERIC ;
ALPHA : [a-zA-Z];
fragment ALPHANUMERIC : ALPHA (ALLOWEDATTCHAR)* ;
fragment ALLOWEDATTCHAR : '-' | '_'| [0-9] | ALPHA ;

id : ZERO | NUMBER | STRING ;
WS : [ \t\n\r]+ -> skip;
COMMENT : '#' ~( '\r' | '\n' )* -> skip;