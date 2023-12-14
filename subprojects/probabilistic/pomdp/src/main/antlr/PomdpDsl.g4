grammar PomdpDsl;

pomdp:
    DISCOUNT discount=NUMBER
    VALUES values=Value_tail
    STATES ((numberOfStates=NUMBER) | ((states+=STRING)+))
    ACTIONS ((numberOfActions=NUMBER) | ((actions+=id)+))
    OBSERVATIONS ((numberOfObservations=NUMBER) | ((observations+=id)+))
    (START (beliefStateProbs+=NUMBER)+)?
    (transitions+=transition)+
    (observationfunction+=observation)+
    (rewardfunction+=reward)+
;

Value_tail : REWARD|COST;
sourceWithProbs : (probs+=NUMBER)+ ';';
transition :
    (T action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker prob=NUMBER)
    | (T action=idOrJoker COLON source=idOrJoker (probs+=NUMBER)+)
    | (T action=idOrJoker (sources+=sourceWithProbs)+)
;
destinationWithProbs : (probs+=NUMBER)+ ';';
observation :
    (O action=idOrJoker COLON destination=idOrJoker (probs+=NUMBER)+)
    | (O action=idOrJoker (destinations+=destinationWithProbs)+)
;
destinationWithRewards : (rews+=NUMBER)+ ';';
reward :
    (R action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker COLON obs=idOrJoker rew=NUMBER)
    | (R action=idOrJoker COLON source=idOrJoker COLON destination=idOrJoker (rews+=NUMBER)+)
    | (R action=idOrJoker COLON source=idOrJoker (destinations+=destinationWithRewards)+)
;

idOrJoker : id|JOKER;

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
STRING : ALPHANUMERIC ;
ALPHA : [a-zA-Z];
fragment ALPHANUMERIC : ALPHA (ALLOWEDATTCHAR)* ;
fragment ALLOWEDATTCHAR : '-' | '_'| [0-9] | ALPHA ;
WS : [ \t\n\r]+ -> skip;
COMMENT : '#' ~( '\r' | '\n' )* -> skip;
id : NUMBER | STRING ;
NUMBER : '-'? [0-9]+('.'[0-9]*)?;
//NUMBER : [1-9][0-9]* ;
//NUMBER: '-'? NUMBER('.'NUMBER)?;
//ZERO: '0';
//ONE: '1';
