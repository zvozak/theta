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
    (rewardfunction+=reward)+
;

Value_tail : REWARD|COST;
sourceWithProbs : (probs+=PROB)+ ';';
transition :
    (T action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker prob=PROB)
    | (T action=idOrJoker COLON source=idOrJoker (probs+=PROB)+)
    | (T action=idOrJoker (sources+=sourceWithProbs)+)
;
destinationWithProbs : (probs+=PROB)+ ';';
observation :
    (O action=idOrJoker COLON destination=idOrJoker (probs+=PROB)+)
    | (O action=idOrJoker (destinations+=destinationWithProbs)+)
;
destinationWithRewards : (rews+=(NUMBER|FLOAT))+ ';';
reward :
    (R action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker COLON obs=idOrJoker rew=(NUMBER|FLOAT))
    | (R action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker (rews+=NUMBER|FLOAT)+)
    | (R action=idOrJoker COLON source=idOrJoker (destinations+=destinationWithRewards)+)
;

idOrJoker : id|JOKER;
id : ZERO | NUMBER | STRING ;

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
R : 'R' COLON  ;
REWARDS : 'R' COLON  ;
JOKER : '*';
NUMBER : [1-9][0-9]* ;
FLOAT: '-'? NUMBER('.'NUMBER)?;
ZERO: '0';
ONE: '1';
STRING : ALPHANUMERIC ;
ALPHA : [a-zA-Z];
fragment ALPHANUMERIC : ALPHA (ALLOWEDATTCHAR)* ;
fragment ALLOWEDATTCHAR : '-' | '_'| [0-9] | ALPHA ;
WS : [ \t\n\r]+ -> skip;
COMMENT : '#' ~( '\r' | '\n' )* -> skip;