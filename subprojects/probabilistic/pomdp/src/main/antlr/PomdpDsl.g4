grammar PomdpDsl;

pomdp:
    DISCOUNT discount=PROB
    VALUES values=value_tail
    STATES (numberOfStates=NUMBER|(states=STRING)+)
    ACTIONS (numberOfActions=NUMBER|(actions+=STRING)+)
    OBSERVATIONS (numberOfObservations=NUMBER|(observations=STRING)+)
    START beliefStateProbs
    (
        (transitions+=transition)+ |
        (transitions+=transition)+ |
        (transitions+=transition)+
    )

    ;

    value_tail : REWARD|COST;
    beliefStateProbs : (PROB ' ')* PROB;
    sourceWithProbs : (probs+=PROB ' ')+ NEWLINE+;
    action : ACTION;
    source : STATE;
    destination : STATE;
    prob : PROB;
    transition :
        T action COLON source COLON destination COLON prob |
        T action COLON source COLON (probs+=prob)+ NEWLINE+ |
        T action NEWLINE+ (sources+=sourceWithProbs)
    ;

    COLON : ':';
    DISCOUNT : 'discount' COLON WS ;
    VALUES : 'values' COLON WS ;
    REWARD : 'reward';
    COST : 'cost';
    STATES : 'states' COLON WS ;
    ACTIONS : 'actions' COLON WS ;
    OBSERVATIONS : 'observations' COLON WS ;
    START : 'start' COLON NEWLINE ;
    T : 'T' COLON WS ;
    OBSERVATION : 'O' COLON WS ;
    REWARDS : 'R' COLON WS ;
    JOKER : '*';

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