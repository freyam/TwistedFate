import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.*;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.misc.Pair;
import genius.core.misc.Range;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.timeline.DiscreteTimeline;
import genius.core.utility.AdditiveUtilitySpace;

import java.text.DecimalFormat;
import java.util.*;

public class TwistedFate extends AbstractNegotiationParty {
    /**
     * Context Information
     * <p>
     * CTX_TOTAL_ROUNDS: Total number of rounds in the negotiation
     * CTX_SESSION_PARTIES: Total number of parties in the negotiation
     * CTX_MIN_UTILITY: Minimum utility that can be achieved in the negotiation
     * CTX_MAX_UTILITY: Maximum utility that can be achieved in the negotiation
     * CTX_RESERVATION_VALUE: Reservation value of the agent
     * CTX_DISCOUNT_FACTOR: Discount factor of the agent
     * CTX_CONCESSION_FACTOR: Concession factor of the agent
     * CTX_ALL_BIDS: All possible bids in the negotiation
     * CTX_BEST_BID: Best bid of the agent
     * CTX_TIMELINE: Timeline of the negotiation
     * CTX_OUTCOME_SPACE: Outcome space of the negotiation
     * CTX_ISSUES: Issues of the negotiation
     **/
    int CTX_TOTAL_ROUNDS, CTX_SESSION_PARTIES;
    double CTX_MIN_UTILITY, CTX_MAX_UTILITY, CTX_RESERVATION_VALUE, CTX_DISCOUNT_FACTOR, CTX_CONCESSION_FACTOR;
    List<Bid> CTX_ALL_BIDS;
    Bid CTX_BEST_BID;
    DiscreteTimeline CTX_TIMELINE;
    SortedOutcomeSpace CTX_OUTCOME_SPACE;
    List<Issue> CTX_ISSUES;

    /**
     * Opponent Modelling
     * <p>
     * OPP_MODELS: List of opponent models (one for each opponent)
     * OPP_ACTION: Last action of the opponent
     * OPP_MODEL_AVAIL: Availability of the opponent model
     **/
    Map<AgentID, PreferenceModel> OPP_MODELS;
    Pair<AgentID, Bid> OPP_ACTION;
    boolean OPP_MODEL_AVAIL = true;

    DecimalFormat df = new DecimalFormat("0.000");
    String RESET = "\033[0m",
            BLUE = "\033[1;34m",
            PURPLE = "\033[1;35m",
            RED = "\033[1;31m",
            GREEN = "\033[1;32m",
            YELLOW = "\033[1;33m",
            CYAN = "\033[1;36m",
            WHITE = "\033[1;37m";

    boolean LOGGING = true;

    void log(Object o) {
        if (LOGGING) System.out.println(o);
    }

    @Override
    public String getDescription() {
        return "Only a fool plays the hand he’s dealt";
    }

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        CTX_TIMELINE = (DiscreteTimeline) info.getTimeline();
        CTX_TOTAL_ROUNDS = CTX_TIMELINE.getTotalRounds() - 1;
        CTX_OUTCOME_SPACE = new SortedOutcomeSpace(utilitySpace);

        List<BidDetails> bids = CTX_OUTCOME_SPACE.getAllOutcomes();

        CTX_ALL_BIDS = new ArrayList<>();
        for(BidDetails bid : bids)
            CTX_ALL_BIDS.add(bid.getBid());

        CTX_BEST_BID = bids.get(0).getBid();

        CTX_MIN_UTILITY = getUtility(bids.get(bids.size() - 1).getBid());
        CTX_MAX_UTILITY = getUtility(bids.get(0).getBid());
        CTX_RESERVATION_VALUE = utilitySpace.getReservationValueUndiscounted();
        CTX_DISCOUNT_FACTOR = utilitySpace.getDiscountFactor();

        CTX_CONCESSION_FACTOR = 0.10;

        CTX_ISSUES = utilitySpace.getDomain().getIssues();

        for (Issue issue : CTX_ISSUES) {
            if (!(issue instanceof IssueDiscrete)) {
                OPP_MODEL_AVAIL = false;
                break;
            }
        }

        if (OPP_MODEL_AVAIL)
            OPP_MODELS = new HashMap<>();

        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        CTX_ISSUES.sort(Comparator.comparingDouble(o -> -1 * additiveUtilitySpace.getWeight(o.getNumber())));

        log(BLUE + "-------------------------------------------SESSION INFO-------------------------------------------");

        log("Total Rounds: " + CTX_TOTAL_ROUNDS);
        log("");

        log("Preference Profile");
        for (Issue issue : CTX_ISSUES) {
            String IssueName = issue.getName();
            double IssueWeight = additiveUtilitySpace.getWeight(issue.getNumber()) * 100;
            String myPreference = CTX_BEST_BID.getValue(issue.getNumber()).toString();
            log(IssueName + " | Weight: " + df.format(IssueWeight) + "% | My Preference: " + myPreference);
        }
        log("");

        log("Minimum Utility: " + df.format(CTX_MIN_UTILITY) + " | Maximum Utility: " + df.format(CTX_MAX_UTILITY));
        log("Reservation Value: " + df.format(CTX_RESERVATION_VALUE) + " | Discount Factor: " + df.format(CTX_DISCOUNT_FACTOR));

        log("-------------------------------------------SESSION INFO-------------------------------------------" + RESET);
        log("");
    }

    /**
     * Upon our turn, we propose a bid or accept if its favourable
     *
     * @param possibleActions
     *            List of all actions possible.
     *
     * @return Action to be taken
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        double currentTime = CTX_TIMELINE.getTime();
        int currentRound = CTX_TIMELINE.getRound();

        if (OPP_ACTION == null) {
            log(PURPLE + "[" + currentRound + "] " + CYAN + "START (" + df.format(getUtilityWithDiscount(CTX_BEST_BID)) + ")" + RESET);
            return new Offer(getPartyId(), CTX_BEST_BID);
        }

        double oppBidUtility = getUtilityWithDiscount(OPP_ACTION.getSecond());

        if (oppBidUtility >= 0.85 || currentRound == CTX_TOTAL_ROUNDS) {
            log(GREEN + "-> ACCEPT (" + df.format(oppBidUtility) + ")");
            return new Accept(getPartyId(), OPP_ACTION.getSecond());
        }

        if (currentTime < 0.25) {
            log(CYAN + "-> OFFER (" + df.format(getUtilityWithDiscount(CTX_BEST_BID)) + ")" + RESET);
            return new Offer(getPartyId(), CTX_BEST_BID);
        }

        Bid myNextBid = findOptimalBid(currentTime, OPP_ACTION.getFirst());
        double myNextBidUtility = getUtilityWithDiscount(myNextBid);

        if (oppBidUtility >= myNextBidUtility || currentRound == CTX_TOTAL_ROUNDS) {
            log(GREEN + "ACCEPT (" + df.format(oppBidUtility) + ")" + RESET);
            return new Accept(getPartyId(), OPP_ACTION.getSecond());
        }

        return new Offer(getPartyId(), myNextBid);
    }

    /**
     * Upon receiving an action from the opponent, update the opponent model.
     *
     * @param sender
     *            The initiator of the action.This is either the AgentID, or
     *            null if the sender is not an agent (e.g., the protocol).
     *
     * @param action
     *            The action performed.
     */
    @Override
    public void receiveMessage(AgentID sender, Action action) {
        super.receiveMessage(sender, action);

        int currentRound = CTX_TIMELINE.getRound();

        if(!OPP_MODELS.containsKey(sender)) {
            OPP_MODELS.put(sender, new PreferenceModel(utilitySpace.getDomain().getIssues()));
        }

        if (action instanceof Inform) {
            CTX_SESSION_PARTIES = (Integer) ((Inform) action).getValue();

            log("");
            log(BLUE + "-------------------------------------------SESSION INFO-------------------------------------------");
            log("Session Parties: " + CTX_SESSION_PARTIES);
            log("-------------------------------------------SESSION INFO-------------------------------------------" + RESET);
            log("");
        }

        if (action instanceof Offer) {
            OPP_ACTION = new Pair<>(sender, ((Offer) action).getBid());

            log(PURPLE + "[" + currentRound + "][" + OPP_ACTION.getFirst() + "] " + WHITE + "RECEIVE (" + df.format(getUtilityWithDiscount(OPP_ACTION.getSecond())) + ")" + RESET);

            if (OPP_MODEL_AVAIL) {
                OPP_MODELS.get(sender).addToBidHistory(OPP_ACTION.getSecond());
                OPP_MODELS.get(sender).update(OPP_ACTION.getSecond());
            }
        }

        if (action instanceof Accept) {
            log(PURPLE + "[" + currentRound + "][" + sender + "] " + GREEN + "ACCEPT" + RESET);
        }

        if (action instanceof EndNegotiationWithAnOffer) {
            log(PURPLE + "[" + currentRound + "] " + RED + "END NEGOTIATION");
            new Accept(getPartyId(), ((EndNegotiationWithAnOffer) action).getBid());
        }
    }

    /**
     * Upon the end of the negotiation, print the final results.
     *
     * @param acceptedBid
     *            the final accepted bid, or null if no agreement was reached.
     */
    @Override
    public HashMap<String, String> negotiationEnded(Bid acceptedBid) {
        log("");
        log(BLUE + "-------------------------------------------SESSION INFO-------------------------------------------");

        if (acceptedBid != null)
            log(GREEN + "Agreement: " + acceptedBid + "(" + getUtilityWithDiscount(acceptedBid) + ")" + RESET);
        else
            log(RED  + "Negotiation ended without an agreement" + RESET);

        log(BLUE + "-------------------------------------------SESSION INFO-------------------------------------------" + RESET);
        log("");

        return null;
    }

    /**
     * Find the optimal bid for the current round based on time-based and
     * behaviour-based utility.
     *
     * @param currentTime
     *             The current time in the negotiation.
     * @param sender
     *             The initiator of the action.This is either the AgentID, or
     *             null if the sender is not an agent (e.g., the protocol).
     */
    Bid findOptimalBid(double currentTime, AgentID sender) {
        double timeBasedUtility = CTX_MIN_UTILITY + ((CTX_MAX_UTILITY - CTX_MIN_UTILITY) * (1 - Math.pow(currentTime, 1 / CTX_CONCESSION_FACTOR)));
        timeBasedUtility = Math.max(timeBasedUtility, CTX_RESERVATION_VALUE);

        if (OPP_MODEL_AVAIL) {
            Bid optimalBid = OPP_MODELS.get(sender).getOptimalBid(timeBasedUtility);
            double optimalBidUtility = getUtilityWithDiscount(optimalBid);

            Bid opponentBestBid = OPP_MODELS.get(sender).getBestBid();
            double opponentBestBidUtility = getUtilityWithDiscount(opponentBestBid);

            Bid opponentRunningBid = OPP_MODELS.get(sender).getRunningBid();
            double opponentRunningBidUtility = getUtilityWithDiscount(opponentRunningBid);

            optimalBid = optimalBidUtility > opponentBestBidUtility ? optimalBid : opponentBestBidUtility > opponentRunningBidUtility ? opponentBestBid : opponentRunningBid;
            optimalBidUtility = getUtilityWithDiscount(optimalBid);

            log(CYAN + "-> " + YELLOW + "[BEST:" + df.format(getUtilityWithDiscount(CTX_BEST_BID)) + ", LONG TERM: " + df.format(opponentBestBidUtility) + ", SHORT TERM: " + df.format(opponentRunningBidUtility) + ", OPTIMAL: " + df.format(optimalBidUtility) + "]" + CYAN + " -> OFFER (" + df.format(optimalBidUtility) + ")" + RESET);

            return optimalBid;
        }

        return CTX_OUTCOME_SPACE.getBidNearUtility(timeBasedUtility).getBid();
    }

    class PreferenceModel {
        /**
         * Preference Model of an Agent
         * <p>
         * frequencyTable: A table that stores the frequency of each issue value
         * bidsReceived: Total number of bids received from this agent (aka. totalBids)
         * domainIssues: The issues of the domain
         * issueWeights: The weights of the issues
         * bidHistory: A list of bids received from this agent
         */
        final List<Map<String, Integer>> frequencyTable = new ArrayList<>();
        int bidsReceived, totalBids;
        List<Issue> domainIssues;
        double[] issueWeights;

        List<Bid> bidHistory = new ArrayList<>();

        PreferenceModel(List<Issue> domainIssues) {
            bidsReceived = 0;

            this.domainIssues = domainIssues;
            issueWeights = new double[domainIssues.size()];
            for (int i = 0; i < domainIssues.size(); ++i) {
                issueWeights[i] = 0;
            }

            for (Issue domainIssue : domainIssues) {
                IssueDiscrete issue = (IssueDiscrete) domainIssue;
                int xValues = issue.getNumberOfValues();
                Map<String, Integer> issueFrequency = new HashMap<>();

                for (int j = 0; j < xValues; ++j)
                    issueFrequency.put(issue.getValue(j).toString(), 0);

                frequencyTable.add(issueFrequency);
            }
        }

        /**
         * Update the preference model with a new bid.
         *
         * @param bid
         *            The bid to update the model with.
         */
        void update(Bid bid) {
            if (bid == null || bid.getValues().size() != domainIssues.size())
                return;

            bidsReceived++;
            totalBids = bidsReceived;

            for (int i = 0; i < domainIssues.size(); ++i) {
                String key = bid.getValue(i + 1).toString();
                frequencyTable.get(i).put(key, frequencyTable.get(i).get(key) + 1);

                for (int j = 0; j < domainIssues.size(); ++j) {
                    double mean = 0;
                    for (Integer value : frequencyTable.get(j).values())
                        mean += value;
                    mean /= frequencyTable.get(j).size();

                    double variance = 0;
                    for (Integer value : frequencyTable.get(j).values())
                        variance += Math.pow(value - mean, 2);
                    variance /= frequencyTable.get(j).size();

                    issueWeights[j] = Math.sqrt(variance);
                }
            }
        }

        /**
         * Get the estimated utility of a bid based on the frequency table.
         *
         * @param bid
         *            The bid to estimate the utility of.
         * @return
         *            The estimated utility of the bid.
         */
        double getEstimatedUtility(Bid bid) {
            double utility = 0.0;

            if (totalBids == 0) return utility;

            double sumOfIssueUtility = 0;
            double sumOfWeights = 0;

            for (double weight : issueWeights)
                sumOfWeights += weight;

            double[] normalizedIssueWeights = new double[issueWeights.length];
            for (int i = 0; i < issueWeights.length; ++i)
                normalizedIssueWeights[i] = issueWeights[i] / sumOfWeights;

            for (int i = 0; i < domainIssues.size(); ++i) {
                String key = bid.getValue(i + 1).toString();
                Integer currentValue = frequencyTable.get(i).get(key);
                double issueUtility = (double) currentValue / totalBids;
                sumOfIssueUtility += issueUtility * normalizedIssueWeights[i];
            }

            utility = sumOfIssueUtility / domainIssues.size();
            return utility;
        }

        /**
         * Get the optimal bid based on the estimated utility.
         *
         * @param timeBasedUtility
         *             The utility of the bid based on the time.
         *
         * @return The optimal bid.
         */
        Bid getOptimalBid(double timeBasedUtility) {
            double neighbourhoodRadius = Math.max(0.1, 0.1 * (1 - timeBasedUtility));

            Range neighbourhood = new Range(Math.max(timeBasedUtility - neighbourhoodRadius, CTX_MIN_UTILITY), Math.min(timeBasedUtility + neighbourhoodRadius, CTX_MAX_UTILITY));

            List<Bid> neighbourhoodBids = new ArrayList<>();

            for (Bid bid : CTX_ALL_BIDS) {
                double bidUtility = getUtilityWithDiscount(bid);
                if(bidUtility >= neighbourhood.getLowerbound() && bidUtility <= neighbourhood.getUpperbound())
                    neighbourhoodBids.add(bid);
            }

            if (neighbourhoodBids.size() == 1) {
                return neighbourhoodBids.get(0);
            }

            if (neighbourhoodBids.size() == 0) {
                return CTX_OUTCOME_SPACE.getBidNearUtility(timeBasedUtility).getBid();
            }

            Bid optimalBid = null;

//            double maxEstimatedUtility = 0;
//            for (Bid bid : neighbourhoodBids) {
//                double estimatedUtility = getEstimatedUtility(bid);
//                if (estimatedUtility > maxEstimatedUtility) {
//                    maxEstimatedUtility = estimatedUtility;
//                    optimalBid = bid;
//                }
//            }

            double totalUtility = 0.0;
            double[] sumOfUtilities = new double[neighbourhoodBids.size()];
            for (Bid bid : neighbourhoodBids) {
                sumOfUtilities[neighbourhoodBids.indexOf(bid)] = getEstimatedUtility(bid);
                totalUtility += sumOfUtilities[neighbourhoodBids.indexOf(bid)];
            }

            for (int i = 0; i < sumOfUtilities.length; ++i)
                sumOfUtilities[i] /= totalUtility;

            double random = Math.random();
            double sum = 0.0;
            for (int i = 0; i < sumOfUtilities.length; ++i) {
                sum += sumOfUtilities[i];
                if (sum >= random || i == sumOfUtilities.length - 1) {
                    optimalBid = neighbourhoodBids.get(i);
                    break;
                }
            }

            return optimalBid;
        }

        void addToBidHistory(Bid bid) {
            bidHistory.add(bid);
        }

        /**
         * Get the bid from the bid history with the highest utility for us.
         *
         * @return The bid with the highest utility for us.
         */
        Bid getBestBid() {
            Bid bestBid = null;
            double bestUtility = 0.0;
            for (Bid bid : bidHistory) {
                double utility = getUtilityWithDiscount(bid);
                if (utility > bestUtility) {
                    bestUtility = utility;
                    bestBid = bid;
                }
            }
            return bestBid;
        }

        /**
         * Get the running average of the utility of the bids in the bid history.
         *
         * @return The bid with utility near the running average.
         */
        Bid getRunningBid() {
            int n = Math.min(3, bidHistory.size());
            List<Bid> lastNBids = bidHistory.subList(bidHistory.size() - n, bidHistory.size());

            double averageUtility = 0.0;
            for (Bid bid : lastNBids) {
                averageUtility += getUtilityWithDiscount(bid);
            }
            averageUtility /= n;

            Bid optimalBid = null;
            double minDistance = Double.MAX_VALUE;
            for (Bid bid : lastNBids) {
                double distance = Math.abs(getUtilityWithDiscount(bid) - averageUtility);
                if (distance < minDistance) {
                    minDistance = distance;
                    optimalBid = bid;
                }
            }
            return optimalBid;
        }

//        double getSimilarity(Bid bid1, Bid bid2) {
//            double similarity = 0.0;
//            for (int i = 0; i < domainIssues.size(); ++i) {
//                String key1 = bid1.getValue(i + 1).toString();
//                String key2 = bid2.getValue(i + 1).toString();
//                if (key1.equals(key2))
//                    similarity += 1.0;
//            }
//            return similarity / domainIssues.size();
//        }
    }
}