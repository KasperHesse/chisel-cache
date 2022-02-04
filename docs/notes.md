# Notes

## Cache
The initial cache design is a direct-mapped cache only supporting word-aligned access.
But, it should hopefully become a modular cache that supports N-way cache structure,
as well as non-aligned memory accesses. We'll see how difficult that is.

Initial layout:
- Word size: 32 bits
- Word per block: 4
- Blocks: 256
- Total size: 256 block * 4 words/block * 4 bytes/word = 4096 bytes
- write-back operations

Using the above, we get
- 2 bits for byte offset
- 2 bits for block offset
- 7 bits for index
- 21 bits for tag


## Cache controller
Note: Heavily inspired by H&P chapter 5.9, at least for the initial design

The cache assumes the following interface between processor and cache
- boolean read/write line
- 32-bit address
- 32-bit write data from processor
- 32-bit read data from processor
- ready/valid handshake

And assumes the following interface between cache and main memory
- boolean read/write line
- 32-bit address
- 32-bit write data to memory
- 32-bit read data from memory
- ready/valid handshake

## Replacement
The replacement module is used to choose which cache block a load should
go into when a way-separated cache is being used. When the cache is direct-mapped,
the replacement is simple, as only one memory block is used.

# Structure
- ache holds memory block that is the cache itself. 
- The controller contains an FSM used to control the cache and replacement module, as well as dirty/valid bits
- The replacement module contains data used for replacement purposes


## Reading
When an operation is issued, this is forwarded from the Cache module to 
the controller module. The controller module will check if the tag
at the current index matches, and whether the valid bit is set
- If true, on the next clock cycle, it will set valid=true and data will
  be read from memory
- If false, it will go into a load-state, issuing a load to main memory for
  the addresses corresponding to the current cache block.
  - Depending on the ratio between cache-to-memory bus width and block size, N reads will be issued
  - These reads are generated in sequence: It is assumed that a buffer/fifo will keep these reads and they do not get lost
  - Addresses are generated in controller module
  - When this arrives from memory, it will be stored into the cache by replacement module.
  - The Replacement module is used to select which cache block should be updated
    in case of a way-separated cache
  - Once all N reads have arrived, replacement module signals to controller that it may continue
- Once the load operations is finished, we issue a new read, finding
  valid data and returning this to the processor

- If the field being replaced contains dirty data, we first store that data
- back into memory, then perform the memory read. 

## Storing
General outline as above
- If that address is already stored in cache, modify the data and return to processor
- If data is not stored, first issue a load, then modify data in cache
  - Loading data follows same procedure as described above

- A write-operation should be possible to execute in parallel with normal
  processor operations. If another cache operation is started while the 
  cache is busy processing a request, a signal TBD should instruct the
  processor to stall.


# Work
## Implement read-behaviour
- Declare memory in cache OK
- Set up read accessor based on addr OK
- Declare tag, dirty, valid bits in controller OK
- Create state FSM in controller ALMOST OK
- When fetching data, FSM should issue the required number of reads
- It should then enter a waiting state, until replacement module has noticed that all read data
  has arrived. Replacement module should notify controller, which will then change state