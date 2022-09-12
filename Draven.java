import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

import java.util.List;

public class Draven extends AbstractNegotiationParty {
    Bid lastBid; // offer on the table
    Bid desiredBid; // the bid we want to reach
    double desiredUtility; // the utility we want to reach

    @Override
    public void init(NegotiationInfo info) { // called when the negotiation starts, useful to initialize some variables
        super.init(info);
        try {
            desiredBid = utilitySpace.getMaxUtilityBid(); // desired bid = the bid with the highest utility
            desiredUtility = utilitySpace.getUtility(desiredBid); // get the utility of the desired bid
        } catch (Exception e) {
            e.printStackTrace();
            desiredUtility = 0.95;
        }
    }


    /*
     * Draven is a stubborn agent, he will only accept a bid if it is better than or equal to his desired bid
     * He only offers bids that are better than or equal to his desired bid
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) { // called when it is this agent's turn to choose an action, and returns the chosen action
        try {
            if (lastBid == null) // if we are starting the negotiation
                return new Offer(getPartyId(), desiredBid); // offer the best bid we have

            double lastBidUtility = getUtility(lastBid); // get the utility of the last bid

            if (lastBidUtility == desiredUtility) // if the offer on the table is acceptable
                return new Accept(getPartyId(), lastBid); // accept the offer
        } catch (Exception e) {
            e.printStackTrace();
            return new Accept(getPartyId(), lastBid); // if something goes wrong, accept the offer
        }

        return new Offer(getPartyId(), desiredBid); // make an offer that maximizes our utility
    }

    @Override
    public void receiveMessage(AgentID sender, Action action) { // called when another agent takes an action, useful for keeping track of the negotiation
        if (action instanceof Offer) // if the action is an offer
            lastBid = ((Offer) action).getBid(); // store the bid
    }

    @Override
    public String getDescription() { // called when the user requests the description of the agent, useful to display in the GUI
        return "Draven does it all... with style!";
    }
}